package com.itwray.study.thrid.dingtalk.frame.util;

import cn.hutool.crypto.digest.MD5;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.dingtalkworkflow_1_0.models.FormComponentProps;
import com.aliyun.dingtalkworkflow_1_0.models.StartProcessInstanceRequest;
import com.itwray.study.thrid.dingtalk.frame.annotation.FormComponent;
import com.itwray.study.thrid.dingtalk.frame.client.DingTalkStorageClient;
import com.itwray.study.thrid.dingtalk.frame.config.DingTalkCommonConstant;
import com.itwray.study.thrid.dingtalk.frame.model.AbstractFileDto;
import com.itwray.study.thrid.dingtalk.frame.model.ComponentType;
import com.itwray.study.thrid.dingtalk.frame.form.ApprovalFormEngine;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.aliyun.dingtalkstorage_1_0.models.CommitFileResponseBody.CommitFileResponseBodyDentry;
import static com.aliyun.dingtalkworkflow_1_0.models.StartProcessInstanceRequest.StartProcessInstanceRequestFormComponentValues;

/**
 * 钉钉表单工具
 *
 * @author wangfarui
 * @since 2023/8/3
 */
public abstract class DingTalkFormUtil {

    private static final MD5 MD5_HELPER = MD5.create();

    /**
     * 生成表单控件
     */
    public static com.aliyun.dingtalkworkflow_1_0.models.FormComponent generateFormComponent(Field field) {
        FormComponent formComponent = field.getAnnotation(FormComponent.class);
        if (formComponent == null) return null;
        // 钉钉表单控件
        com.aliyun.dingtalkworkflow_1_0.models.FormComponent dingTalkFormComponent = new com.aliyun.dingtalkworkflow_1_0.models.FormComponent();
        FormComponentProps formComponentProps = new FormComponentProps()
                .setComponentId(determineComponentId(formComponent))
                .setLabel(formComponent.value())
                .setRequired(formComponent.required());
        // 自动识别类型
        if (ComponentType.AUTO.equals(formComponent.componentType())) {
            // 表格控件
            if (Collection.class.isAssignableFrom(field.getType())) {
                if (dealWithTableFieldComponent(field, dingTalkFormComponent, formComponentProps)) {
                    return null;
                }
            }
            // 附件控件
            else if (AbstractFileDto.class.isAssignableFrom(field.getType())) {
                dingTalkFormComponent.setComponentType(ComponentType.DDAttachment.name());
            }
            // 默认单行输入框控件
            else {
                dingTalkFormComponent.setComponentType(ComponentType.TextField.name());
            }
        }
        // 表格控件类型
        else if (ComponentType.TableField.equals(formComponent.componentType())) {
            if (dealWithTableFieldComponent(field, dingTalkFormComponent, formComponentProps)) {
                return null;
            }
        }
        // 指定表单控件类型
        else {
            dingTalkFormComponent.setComponentType(formComponent.componentType().name());
        }
        return dingTalkFormComponent.setProps(formComponentProps);
    }

    /**
     * 生成默认表单控件
     */
    public static List<com.aliyun.dingtalkworkflow_1_0.models.FormComponent> generateDefaultFormComponents() {
        FormComponentProps formComponentProps = new FormComponentProps()
                .setLabel(DingTalkCommonConstant.SELF_RELATE_FIELD_NAME)
                .setRequired(false);
        com.aliyun.dingtalkworkflow_1_0.models.FormComponent formComponent = new com.aliyun.dingtalkworkflow_1_0.models.FormComponent()
                .setComponentType(ComponentType.RelateField.name())
                .setProps(formComponentProps);
        return Collections.singletonList(formComponent);
    }

    /**
     * 生成表单控件值
     */
    @SuppressWarnings("unchecked")
    public static StartProcessInstanceRequestFormComponentValues generateFormComponentValues(Field field, ApprovalFormEngine approvalForm) {
        FormComponent formComponent = field.getAnnotation(FormComponent.class);
        if (formComponent == null) return null;

        StartProcessInstanceRequestFormComponentValues formComponentValues = new StartProcessInstanceRequestFormComponentValues()
                .setId(determineComponentId(formComponent))
                .setName(formComponent.value())
                .setComponentType(determineComponentType(field).name());

        field.setAccessible(true);
        Object o;
        try {
            o = field.get(approvalForm);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("无法访问字段[" + field.getName() + "]", e);
        }
        if (o == null) {
            if (formComponent.required()) {
                throw illegalComponent(formComponent);
            }
            return null;
        }
        // 集合 setDetails
        if (o instanceof Collection) {
            Collection<Object> o1 = (Collection<Object>) o;
            if (o1.isEmpty()) {
                formComponentValues.setValue(JSON.toJSONString(Collections.emptyList()));
            } else {
                Object oType = o1.stream().findAny().get();
                boolean anyMatch = Arrays.stream(oType.getClass().getDeclaredFields()).anyMatch(t ->
                        t.isAnnotationPresent(FormComponent.class)
                );
                // 集合中为非自定义表单控件对象, 例如List<AbstractFileDto>
                if (!anyMatch) {
                    if (oType instanceof AbstractFileDto) {
                        List<JSONObject> fileList = new ArrayList<>(o1.size());
                        for (Object o2 : o1) {
                            JSONObject attachmentJson = uploadFileDto((AbstractFileDto) o2);
                            fileList.add(attachmentJson);
                        }
                        formComponentValues.setValue(JSON.toJSONString(fileList));
                        return formComponentValues;
                    } else {
                        throw new IllegalArgumentException("不支持除List<AbstractFileDto>以外的集合对象");
                    }
                } else {
                    List<StartProcessInstanceRequest.StartProcessInstanceRequestFormComponentValuesDetails> detailsList = new ArrayList<>();
                    List<List<StartProcessInstanceRequest.StartProcessInstanceRequestFormComponentValuesDetails>> valueList = new ArrayList<>(o1.size());
                    for (Object o2 : o1) {
                        Field[] fields = o2.getClass().getDeclaredFields();
                        List<StartProcessInstanceRequest.StartProcessInstanceRequestFormComponentValuesDetails> details = new ArrayList<>(fields.length);
                        for (Field f : fields) {
                            FormComponent fc = f.getAnnotation(FormComponent.class);
                            if (fc == null) continue;
                            f.setAccessible(true);
                            Object o3;
                            try {
                                o3 = f.get(o2);
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException("无法访问字段[" + f.getName() + "]", e);
                            }
                            StartProcessInstanceRequest.StartProcessInstanceRequestFormComponentValuesDetails formComponentValue = new StartProcessInstanceRequest.StartProcessInstanceRequestFormComponentValuesDetails()
                                    .setName(fc.value());
                            formComponentValue.setValue(formatObjectStr(o3, fc));
                            details.add(formComponentValue);
                            detailsList.add(formComponentValue);
                        }
                        valueList.add(details);
                    }
                    formComponentValues.setValue(JSON.toJSONString(valueList));
                    formComponentValues.setDetails(detailsList);
                }
            }
        }
        // 其他都是非集合 setValue
        else if (o instanceof Date) {
            formComponentValues.setValue(formatObjectStr(o, formComponent));
        } else if (o instanceof AbstractFileDto) {
            JSONObject attachmentJson = uploadFileDto((AbstractFileDto) o);
            formComponentValues.setValue(JSON.toJSONString(Collections.singletonList(attachmentJson)));
        } else {
            formComponentValues.setValue(formatObjectStr(o, formComponent));
        }

        return formComponentValues;
    }

    /**
     * 确定表单控件id
     */
    public static String determineComponentId(FormComponent formComponent) {
        if (StringUtils.isNotBlank(formComponent.id())) {
            return formComponent.id();
        }
        return MD5_HELPER.digestHex16(formComponent.value());
    }

    /**
     * 计算表单控件版本号
     */
    public static int computeComponentVersion(Field field) {
        FormComponent formComponent = field.getAnnotation(FormComponent.class);
        if (formComponent == null) {
            return 0;
        }
        String sb = formComponent.value() +
                formComponent.id() +
                formComponent.componentType().name() +
                formComponent.required() +
                formComponent.pattern();
        String hex16 = MD5_HELPER.digestHex16(sb);
        return hex16.hashCode();
    }

    private static JSONObject uploadFileDto(AbstractFileDto fileDto) {
        CommitFileResponseBodyDentry fileDentry = DingTalkStorageClient.uploadFileDto(fileDto);
        // 封装附件组件的值
        JSONObject attachmentJson = new JSONObject();
        attachmentJson.put("fileId", fileDentry.getId());
        attachmentJson.put("fileName", fileDentry.getName());
        attachmentJson.put("fileType", fileDentry.getExtension());
        attachmentJson.put("spaceId", fileDentry.getSpaceId());
        attachmentJson.put("fileSize", fileDentry.getSize());
        return attachmentJson;
    }

    private static ComponentType determineComponentType(Field field) {
        FormComponent formComponent = field.getAnnotation(FormComponent.class);
        if (!ComponentType.AUTO.equals(formComponent.componentType())) {
            return formComponent.componentType();
        }

        Class<?> type = field.getType();
        if (Collection.class.isAssignableFrom(type)) {
            Class<?> genericsClass = getGenericsClass(field);
            if (genericsClass != null && AbstractFileDto.class.isAssignableFrom(genericsClass)) {
                return ComponentType.DDAttachment;
            }
            return ComponentType.TableField;
        }
        if (AbstractFileDto.class.isAssignableFrom(type)) {
            return ComponentType.DDAttachment;
        }
        return ComponentType.TextField;

    }

    private static boolean dealWithTableFieldComponent(Field field,
                                                       com.aliyun.dingtalkworkflow_1_0.models.FormComponent dingTalkFormComponent,
                                                       FormComponentProps formComponentProps) {
        Class<?> genericsClass = getGenericsClass(field);
        if (genericsClass == null) {
            return true;
        }
        if (Collection.class.isAssignableFrom(genericsClass)) {
            throw new IllegalArgumentException("不支持多层集合");
        }
        // 附件集合，默认为附件组件类型
        if (AbstractFileDto.class.isAssignableFrom(genericsClass)) {
            dingTalkFormComponent.setComponentType(ComponentType.DDAttachment.name());
            return false;
        }
        Field[] genericsFields = genericsClass.getDeclaredFields();
        List<com.aliyun.dingtalkworkflow_1_0.models.FormComponent> genericsFormComponent = new ArrayList<>(genericsFields.length);
        for (Field genericsField : genericsFields) {
            com.aliyun.dingtalkworkflow_1_0.models.FormComponent component = generateFormComponent(genericsField);
            if (component != null) {
                verifyTableComponentType(component);
                genericsFormComponent.add(component);
            }
        }
        if (genericsFormComponent.isEmpty()) {
            return true;
        }
        dingTalkFormComponent.setComponentType(ComponentType.TableField.name());
        dingTalkFormComponent.setChildren(genericsFormComponent);
        formComponentProps.setTableViewMode("table");

        return false;
    }

    private static void verifyTableComponentType(com.aliyun.dingtalkworkflow_1_0.models.FormComponent formComponent) {
        String componentType = formComponent.getComponentType();
        if (!componentType.equalsIgnoreCase(ComponentType.TextField.name())
                && !componentType.equalsIgnoreCase(ComponentType.TextareaField.name())) {
            throw new IllegalArgumentException("明细控件目前只支持: 单行输入框、多行输入框。");
        }
    }

    private static Class<?> getGenericsClass(Field field) {
        field.setAccessible(true);
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType type = (ParameterizedType) genericType;
            return (Class<?>) type.getActualTypeArguments()[0];
        }
        return null;
    }

    /**
     * 格式化对象 转为字符串
     *
     * @param o             对象
     * @param formComponent 表单控件
     * @return 字符串
     */
    private static String formatObjectStr(Object o, FormComponent formComponent) {
        if (o == null) {
            if (formComponent.required()) {
                throw illegalComponent(formComponent);
            }
            return "";
        }

        if (o instanceof Date) {
            SimpleDateFormat sdf = new SimpleDateFormat(formComponent.pattern());
            return sdf.format(o);
        }

        String s = o.toString();
        if (StringUtils.isBlank(s)) {
            throw illegalComponent(formComponent);
        }
        return s;
    }

    private static IllegalArgumentException illegalComponent(FormComponent formComponent) {
        return new IllegalArgumentException(String.format("表单控件[%s]为必填项，不能为空", formComponent.value()));
    }

}
