package github.builgen.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.CollectionListModel;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Create by IntelliJ IDEA
 *
 * @Author chenlei
 * @DateTime 2017/8/30 16:59
 * @Description BuilgenAction
 * @github
 */
public class BuilgenAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {

        Project project = e.getData(PlatformDataKeys.PROJECT);
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        PsiFile currentEditorFile = PsiUtilBase.getPsiFileInEditor(editor, project);

        PsiJavaFile element = (PsiJavaFile) currentEditorFile.getOriginalElement();

        PsiClass psiClass = element.getClasses()[0];

        List<PsiField> fields =  (new CollectionListModel(psiClass.getFields())).getItems();

        if(!fields.isEmpty()){
            /**
             * must use WriteCommandAction,or an exception
             */
            (new WriteCommandAction.Simple(psiClass.getProject(), new PsiFile[]{psiClass.getContainingFile()}) {
                protected void run() throws Throwable {
                    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());

                    //gen constructor
                    psiClass.add(elementFactory.createMethodFromText(genNullParamConstructor(psiClass.getName()),psiClass));
                    psiClass.add(elementFactory.createMethodFromText(genConstructor(psiClass.getName(),fields),psiClass));

                    //generate getter/setter
                    genGetterAndSetter(psiClass,fields,elementFactory);
                    //generate builder class
                    genBuilderClass(psiClass,fields,elementFactory);
                }
            }).execute();

        }else{
            Messages.showMessageDialog("No Fields!","Error",Messages.getErrorIcon());
        }

    }

    /**
     * generate builder class
     * @param psiClass
     * @param fields
     * @param elementFactory
     */
    private void genBuilderClass(PsiClass psiClass, List<PsiField> fields, PsiElementFactory elementFactory) {


        String clazzName = psiClass.getName();
        String builderName = clazzName + "Builder";

        PsiClass psiClassBuilder = elementFactory.createClass(builderName);
        psiClassBuilder.add(elementFactory.createFieldFromText(genFiled(clazzName),psiClassBuilder));
        psiClassBuilder.add(elementFactory.createMethodFromText(genBuilderConstructor(builderName,clazzName),psiClassBuilder));

        for(PsiField psiField : fields){
            // field type
            String type = psiField.getType().getPresentableText();
            //field name
            String name = psiField.getName();

            //
            psiClassBuilder.add(elementFactory.createMethodFromText(genBuilderMethod(builderName,clazzName,type,name),psiClassBuilder));
        }

        psiClassBuilder.add(elementFactory.createMethodFromText(genBuildMethod(clazzName),psiClassBuilder));

        psiClass.add(psiClassBuilder);


    }

    /**
     * generate field
     * @param clazzName
     * @return
     */
    private String genFiled(String clazzName) {
        StringBuilder builder = new StringBuilder();

        builder.append("private ").append(clazzName).append(" ").append(firstLowercase(clazzName)).append(";");

        return builder.toString();
    }

    /**
     * generate build() method
     * @param clazzName
     * @return
     */
    private String genBuildMethod(String clazzName) {
        StringBuilder builder = new StringBuilder();

        builder.append("public ").append(clazzName).append(" build(){ return new ").append(clazzName).append("(this.").append(firstLowercase(clazzName)).append(");}");

        return builder.toString();
    }

    /**
     * generate builder method
     * @param builderClazzName
     * @param clazzName
     * @param fieldType
     * @param fieldName
     * @return
     */
    private String genBuilderMethod(String builderClazzName,String clazzName,String fieldType,String fieldName){
        StringBuilder builder = new StringBuilder();

        /**
         * public ${builderClazzName} ${fieldName}(${fieldType} ${fieldName}){
         *     this.${clazzObjectName}.set${filedNameUpperCase}(${fieldName});
         *     return this;
         * }
         */
        builder.append("public ").append(builderClazzName).append(" ").append(fieldName).append("(").append(fieldType).append(" ").append(fieldName)
                .append("){this.").append(firstLowercase(clazzName)).append(".set").append(firstUppercase(fieldName)).append("(")
                .append(fieldName).append(");return this;}");

        return builder.toString();
    }

    /**
     * generate builder constructor
     * @param builderClazzName
     * @param clazzName
     * @return
     */
    private String genBuilderConstructor(String builderClazzName,String clazzName){
        StringBuilder builder = new StringBuilder();

        builder.append("public ").append(builderClazzName).append(" () {this.")
                .append(firstLowercase(clazzName)).append(" = new ").append(clazzName).append("();}");

        return builder.toString();
    }

    /**
     * generate null param constructor
     * @param clazzName
     * @return
     */
    private String genNullParamConstructor(String clazzName){
        StringBuilder builder = new StringBuilder();

        builder.append("public ").append(clazzName).append(" () {}");

        return builder.toString();
    }

    /**
     * generate null param constructor
     * @param clazzName
     * @param fields
     * @return
     */
    private String genConstructor(String clazzName,List<PsiField> fields){
        StringBuilder builder = new StringBuilder();

        List<String> fieldsAssign = new ArrayList<String>();
        for (PsiField field : fields) {
            fieldsAssign.add("this." + field.getName() + " = " + firstLowercase(clazzName) + ".get" + firstUppercase(field.getName()) + "();");
        }

        builder.append("public ").append(clazzName).append(" (").append(clazzName).append(" ").append(firstLowercase(clazzName)).append(") {").append(String.join(" ", fieldsAssign)).append("}");

        return builder.toString();
    }

    /**
     * generate getter and setter
     * @param psiClass
     * @param fields
     * @param elementFactory
     */
    private void genGetterAndSetter(PsiClass psiClass, List<PsiField> fields, PsiElementFactory elementFactory) {

        for (PsiField field : fields) {

            //final no getter/setter
            if(field.getModifierList() == null || !field.getModifierList().hasModifierProperty("final")){
                String getMethodContent = genGetMethod(field.getName(),field.getType().getPresentableText());
                String setMethodContent = genSetMethod(field.getName(),field.getType().getPresentableText());

                psiClass.add(elementFactory.createMethodFromText(getMethodContent,psiClass));
                psiClass.add(elementFactory.createMethodFromText(setMethodContent,psiClass));

            }

        }

    }

    /**
     * generate setter
     * @param name
     * @param type
     * @return
     */
    private String genSetMethod(String name, String type) {
        StringBuilder builder = new StringBuilder();
        builder.append("public void set")
                .append(name.substring(0,1).toUpperCase())
                .append(name.substring(1))
                .append("(").append(type).append(" ").append(name).append(") {this.")
                .append(name).append(" = ").append(name).append(";}");

        return builder.toString();
    }

    /**
     * generate getter
     * @param name
     * @param type
     * @return
     */
    private String genGetMethod(String name, String type) {
        StringBuilder builder = new StringBuilder();
        builder.append("public ").append(type).append(" get")
                .append(name.substring(0,1).toUpperCase())
                .append(name.substring(1))
                .append("() {")
                .append("return this.").append(name).append(";}");

        return builder.toString();
    }

    /**
     * first char to lower case
     * @param string
     * @return
     */
    public String firstLowercase(String string){
        return string.substring(0,1).toLowerCase() + string.substring(1);
    }

    /**
     * first char to upper case
     * @param string
     * @return
     */
    public String firstUppercase(String string){
        return string.substring(0,1).toUpperCase() + string.substring(1);
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        PsiFile currentEditorFile = PsiUtilBase.getPsiFileInEditor(editor, project);

        //if not a java file, set invisible
        e.getPresentation().setEnabledAndVisible(currentEditorFile instanceof PsiJavaFile);
    }
}
