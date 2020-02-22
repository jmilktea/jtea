## 前言
对于webflux项目来说，接口的返回值是Mono或者Flux，swagger接口文档目前还没有正式对此提供支持，仍然处于3.0-SNAPSHOT状态，本篇我们将其集成到webflux工程。与spring mvc的配置基本类似，只不过@EnableSwagger要换成@EnableSwagger2WebFlux

## 实现
- 添加相关依赖和仓库地址
```
<dependency>
      <groupId>io.springfox</groupId>
      <artifactId>springfox-swagger2</artifactId>
      <version>3.0.0-SNAPSHOT</version>
</dependency>
      <dependency>
      <groupId>io.springfox</groupId>
      <artifactId>springfox-swagger-ui</artifactId>
      <version>3.0.0-SNAPSHOT</version>
</dependency>
      <dependency>
      <groupId>io.springfox</groupId>
      <artifactId>springfox-spring-webflux</artifactId>
      <version>3.0.0-SNAPSHOT</version>
</dependency>

<repositories>
    <repository>
        <id>jcenter-snapshots</id>
        <name>jcenter</name>
        <url>http://oss.jfrog.org/artifactory/oss-snapshot-local/</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```
- 添加swagger配置
```
@Configuration
@EnableSwagger2WebFlux
public class SwaggerConfiguration {

    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(new ApiInfoBuilder()
                        .description("jmilktea swagger文档")
                        .title("jmilktea").build())
                .genericModelSubstitutes(Mono.class, Flux.class, Publisher.class)
                .select()
                .apis(RequestHandlerSelectors.any())
                .paths(PathSelectors.any())
                .build();
    }
}
```
- 添加接口
```
@Api("swagger controller")
@RestController
@RequestMapping("swagger")
public class SwaggerController {

    @GetMapping("test")
    @ApiOperation(value = "swagger 接口")
    @ApiImplicitParams(value = {
            @ApiImplicitParam(name = "p1", value = "参数1", required = true, dataType = "int", paramType = "query"),
            @ApiImplicitParam(name = "p2", value = "参数2", required = true, dataType = "string", paramType = "query"),
    })
    public Mono<ReactiveResult<String>> test(Integer p1, String p2) {
        return ReactiveResult.success(p1 + ":" + p2);
    }
}
```

- 效果
![image](https://github.com/jmilktea/jmilktea/blob/master/webflux/images/swagger.png)

