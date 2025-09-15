在笔者的场景中有这么一个需求，需要接收服务A推送过来的数据，查询一些其它信息，对其进行组装，再将数据推送给第三方供应商。没错，就是这么简单。     

但是，需求的麻烦点在于，这个第三方供应商不支持分批推送数据，也就是只能一次性推送数据，每次都是全量覆盖重新插入，先delete再insert的意思，别问为什么不支持分批，问就是不支持。这对于一些小数据的场景也没什么，存储也不在我们这边，但我们要推送的数据量还不小，大概有几万条，没错就是一个大List问题。      

这里有2个大List，服务A推送过来的是一个大List，几万条数据，每条有好几个字段，然后对其遍历、查询、组装后字段更多，推送也供应商又是一个大List，可见对于我们的服务内存占用，以及GC是一个挑战。幸好这个场景的并发量比较低，但大List还是一个麻烦，它会占用高内存，且很可能会逃过young gc进入到老年代，进而频繁触发full gc，影响服务的整体吞吐量。且这个推送数量是可配置的，目前是几万，说不定后面业务就想要推十几万了。      

我们使用feign调用第三方接口，总结一下：   
1、集合很大，几万条数据，每条数据有很多字段，要考虑未来还会增长。   
2、并发很低，时效要求不高，主要考虑内存问题，并发问题可以忽略。   

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/feign/images/feign-big-list-01.png)   

传统的做法肯定不够好，实际上我们测试也发现内存的占用和gc表现都不是很好。我的解决方案是使用“流”来解决，数据都是从流中读取，然后逐行在内存处理，写入临时文件，最后再通过feign将文件流发送出去。可以看到，整个过程并没有大List的存在，虽然逐行读取还是会产生许多临时对象，这是无法避免的，且这些对象可以在年轻代很快被回收，所以没问题。     

伪代码如下：   

```

private final ObjectMapper mapper = new ObjectMapper();

@PostMapping(value = "/process", consumes = MediaType.APPLICATION_JSON_VALUE)
public String process(InputStream inputStream) throws IOException {

	File file = null;
	FileOutputStream fos = null;
	JsonGenerator gen = null;
	try {
		file = File.createTempFile("wa-", ".json");
		fos = new FileOutputStream(file);
		gen = mapper.getFactory().createGenerator(fos);

		// 将数据写入文件
		gen.writeStartObject();
		//写入字段
		gen.writeStringField("tenantId", "123");
		gen.writeStringField("token", "123");

		//开始写入数据
		gen.writeFieldName("data");
		gen.writeStartArray();

		/从流读取数据，写入临时文件
		try (MappingIterator<MyObject> it = mapper.readerFor(MyObject.class).readValues(inputStream)) {
			while (it.hasNext()) {
                //逐行读取，处理数据    
				MyObject obj = it.next();
				mapper.writeValue(gen, obj);
			}
		}

		//结束写入，将缓冲区数据flush到临时文件
		gen.writeEndArray();
		gen.writeEndObject();

		gen.flush();

		//创建FileSystemResource读取临时文件
		FileSystemResource resource = new FileSystemResource(file);

		//推送数据
		serviceClientTest.push(resource);
	} finally {
		if (file != null) {
			file.delete();
		}
		if (fos != null) {
			fos.close();
		}
		if (gen != null) {
			gen.close();
		}
	}

	return "success";
}
```

feign的定义如下：
```
@PostMapping(value = "/pushdata", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
String push(@RequestBody Resource body);
```

在consoller接口，我们使用InputStream，作为参数，然后通过JsonGenerator从流读取出来，处理后将对象写入临时文件，当内存不够用时往往借助磁盘来存储。然后再通过FileSystemResource包装临时文件，调用feign接口我们用Resource作为参数，这是org.springframework.core.io包下的。       

controller接口读取InputStream很好理解，这是我们自己写的代码，那feign又是如何将Resource读取出来调用接口的呢？    
跟踪源码可以发现，最终会调用org.springframework.http.converter.ResourceHttpMessageConverter#writeContent，是一个HttpMessageConverter接口，HttpMessageConverter接口定义了如何读取和写入http请求，ResourceHttpMessageConverter就是专门处理Resource类型参数的，写入的是我们前面写入临时文件的json内容，供应商接口如何定义是不会被影响的。

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/feign/images/feign-big-list-02.png)    

StreamUtils.copy会将InputStream逐步拷贝到OutputStream，每次拷贝4096个字节，对内存压力小。   

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/feign/images/feign-big-list-03.png)    

总结，经过优化后，整体younggc和fullgc次数下降约2/3，效果非常明显，有兴趣或者有以后有类似场景，可以借助这个做法实现。    

