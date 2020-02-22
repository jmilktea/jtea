package com.jmilktea.sample.web.controller;

import com.jmilktea.sample.web.core.ReactiveResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

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
