## 简介
反射在开发中经常用到，尤其是写一些公共组件或者框架经常用到。这一篇文章讨论怎么获取泛型，枚举值等等。

### 代码（这个是一个示例，将要从这个class获取各种值）
```
    @Service
    public interface ITest extends Model<Long,User>  {
        List<String> save(@MyAnnotation("st") String st,List<String> list,@MyAnnotation("id") Long id);
    }
```
以上是一个接口作为一个示例。获取 Long.class 和 User.class
```
   Class<ITest> iTestClass = ITest.class;
   //接口是多继承的 获取多个接口的信息 如果是Class继承 就用 iTestClass.getGenericSuperclass()
   Type[] types = iTestClass.getGenericInterfaces(); 
   //获取第一个接口的泛型
   Type[] actualTypeArguments =( (ParameterizedType) types[0]).getActualTypeArguments();
   //打印出 class java.lang.Long  class com.lhc.model.User
   Stream.of(genericInterfaces).forEach(System.out::println);
```
Class Method Parameter 获取注解
```
    Class<ITest> iTestClass = ITest.class;
    //获取class的注解 是一个数组
    iTestClass.getAnnotations();
    //获取接口方法
    Method[] declaredMethods = iTestClass.getDeclaredMethods();
    //获取方法的注解
    methodInfo.setAnnotations(declaredMethods[0].getAnnotations());
    //获取方法参数的注解 二维数组 和参数一样对应
    Annotation[][] parameterAnnotations = method.getParameterAnnotations();
```
获取方法返回值Class 和Class是否有泛型。参数Class 和参数上面的泛型
```
    //获取返回值类型Class 如果是泛型类的。那么type是ParameterizedType类型。数组就是 GenericArrayType  否 就是CLass类型
    Type genericReturnType = method.getGenericReturnType();
    if (genericReturnType instanceof ParameterizedType) {
        //获取返回值的泛型 这里获取 String.class
        Type[] types = （(ParameterizedTypeImpl) genericReturnType).getActualTypeArguments());
    }
    //获取参数的泛型值 是所有的 如果是泛型类的。那么type是ParameterizedType类型。数组就是 GenericArrayType  否 就是CLass类型
    Type[] genericParameterTypes = method.getGenericParameterTypes();
    Type genericReturnType = genericParameterTypes[0];
    if (genericReturnType instanceof ParameterizedType) {
          //获取返回值的泛型 这里获取 String.class 第二个参数才有
          Type[] types = （(ParameterizedTypeImpl) genericReturnType).getActualTypeArguments());
          //获取参数的 Class  第二个参数List
          ((ParameterizedTypeImpl) type).getRawType();
     }
     //目前方法参数名称是获取不到的。在编译的时候就丢失了。要通过其他办法获取。
```
划重点 Type类型。解析的时候判断类型做不同的处理
   - TypeVariable 普通Class参数。例如：String、User对象
   - ParameterizedType 带泛型的参数。例如 List,Map等等
   - GenericArrayType 数组类型参数。
