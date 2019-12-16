## 简介  
[jasypt](http://www.jasypt.org/) Java Simplified Encryption 是用于加解密的java库，提供一些的算法，高度配置化，让开发人员可以无需关注算法的实现原理即可将其应用到具体项目中，github地址为: https://github.com/ulisesbocchio/jasypt-spring-boot。  
我们可以通过[druid加密数据库账号密码](https://github.com/jmilktea/jmilktea/blob/master/%E5%85%B6%E5%AE%83/druid%E5%AF%86%E7%A0%81%E5%8A%A0%E5%AF%86.md)，但实际项目中还有一些其它信息要加密，如api访问口令，第三方账号等，这些是druid无法完成的，我们可以通过jasypt来实现。

## 实现
1. 添加jasypt springboot starter的依赖
```
<dependency>
  <groupId>com.github.ulisesbocchio</groupId>
  <artifactId>jasypt-spring-boot-starter</artifactId>
  <version>2.0.0</version>
</dependency>
```

2. 首先需要定义一个用于加解密的密钥，这个密钥很重要，拿到它就可以拿到所有明文。所以正式环境一般我们会把它用变量传入，或者配置到系统环境变量中，不会直接写到配置文件，否则稍微有点开发只是的人就可以破解了
```
jasypt:
  encryptor:
    password: 123456
```

3. 对安全信息进行加密。加密后的串最好也不要写到配置文件中，同样是通过变量传入
```
@Autowired
private StringEncryptor encryptor;

String encryptUserName = encryptor.encrypt("root");
String encryptUserPwd = encryptor.encrypt("123456789");
System.out.println("encrypt user-name:" + encryptUserName);
System.out.println("encrypt user-pwd:" + encryptUserPwd);
```

4. 配置。使用ENC(加密后的串)即可。
```
client:
  user-name: ENC(wkpejLzoC4NMM8LLhN1lBg==)
  user-pwd: ENC(iMq3gttb1o3hCsQBSnrrB5M883Z0UtnI)
```

## 原理
jasypt 实现了BeanFactoryPostProcessor接口，BeanFactoryPostProcessor是spring bean生命周期的一部分，它表示所有bean都已经加载，都还没进行初始化，所以可以对bean的属性进行添加或修改。
```
public class EnableEncryptablePropertiesBeanFactoryPostProcessor implements BeanFactoryPostProcessor{
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        EncryptablePropertyResolver propertyResolver = beanFactory.getBean(RESOLVER_BEAN_NAME, EncryptablePropertyResolver.class);
        MutablePropertySources propSources = environment.getPropertySources();
        convertPropertySources(interceptionMode, propertyResolver, propSources);
    }
}
```
可以看到它会拿到当前Environment的PropertySource,PropertySource是用于存放<name,value>的抽象类，我们配置文件定义的就是name-value的结构，经过jasypt包装后会变成一个EncryptablePropertySource，表示可加密的PropertySource
```
public interface EncryptablePropertySource<T> {

    PropertySource<T> getDelegate();

    default Object getProperty(EncryptablePropertyResolver resolver, PropertySource<T> source, String name) {
        Object value = source.getProperty(name);
        if (value instanceof String) {
            String stringValue = String.valueOf(value);
            return resolver.resolvePropertyValue(stringValue);
        }
        return value;
    }
}
```
EncryptablePropertySource 的getProperty方法在获取属性的时候就会被调用，进而进行解密
