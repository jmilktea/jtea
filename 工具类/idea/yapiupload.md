## 简介
在idea定义好接口，通过yapiupload插件一键上次到yapi，生成mock地址，供调用方使用。  
作用：接口文档，联调，mock数据。  
文档地址：https://github.com/diwand/YapiIdeaUploadPlugin/wiki/%E5%BF%AB%E9%80%9F%E4%BD%BF%E7%94%A8   

## 使用  
1. idea 下载安装 yapiupload 插件  
2. 创建yapi项目，并获得项目的token和id，如下  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/idea/images/yapiupload-1.png)  
3. 在项目的.idea目录下，修改misc.xml文件，新增如下配置，token和id是上面获得的  
```
  <component name="yapi">
    <option name="moduleList">order</option>
  </component>
  <component name="order">
    <option name="order.projectToken">bf4d9de81c29de4458cd960aee48b7a1b6242f9e17ec33b59ba53367fce028b4</option>
    <option name="order.projectId">1516</option>
    <option name="order.yapiUrl">http://testyapi.com</option>
    <option name="order.projectType">api</option>
  </component>
```
4. 在项目中选中方法，右键，选中UploadToYapi即可上传。如果没有选择方法或者选择类，将上传所有方法。  
如果参数是实体，希望在yapi显示出备注，写上注释即可。如：
```
@Data
public class ConfigVo {

	/**
	 * 连接
	 */
	private Object connection;

	/**
	 * session
	 */
	private String session;
}
```

