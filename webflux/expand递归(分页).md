## 描述
webflux中递归或者分页可以使用expand来实现，示例代码如下
```
    @Test
    public void testMonoPage() {
        executeMonoPage().block();
    }

    public Mono<Boolean> executeMonoPage() {
        AtomicReference<Integer> index = new AtomicReference<>(0);
        Integer size = 10;
        return queryData(index.get(), size).expand(list -> {
            if (CollectionUtils.isEmpty(list)) {
                //结束递归
                return Mono.empty();
            }
            //递归
            return queryData(index.getAndSet(index.get() + 1), size);
        }).flatMap(list -> {
            //执行完queryData后即会执行遍历，然后再执行下一次queryData
            list.forEach(s -> {
                System.out.println(s);
            });
            return Mono.just(true);
        }).collectList().flatMap(s -> Mono.just(true));
    }

    /**
     * 这里可以是数据库，或者调用接口等耗时操作
     *
     * @param index
     * @param size
     * @return
     */
    public Mono<List<Integer>> queryData(Integer index, Integer size) {
        if (index == 10) {
            return Mono.empty();
        }
        List<Integer> list = new ArrayList<>();
        for (Integer i = index * size; i < ((index + 1) * size); i++) {
            list.add(i);
        }
        return Mono.just(list);
    }
```
