## 简介
使用apache commons csv 生成和读取csv文件，相比excel，csv更加简单，高效。有时候需要写入和导出大量文件时，excel基本会卡死，而使用csv可以非常快。不过简单就没那么强大，例如csv不支持多个sheet。
## 代码
```
<dependency>
  <groupId>org.apache.commons</groupId>
  <artifactId>commons-csv</artifactId>
  <version>1.7</version>
</dependency>
```
```
@SpringBootTest
class ApacheCsvApplicationTests {

    @Data
    @AllArgsConstructor
    class Content {
        private Integer id;
        private String name;
        private BigDecimal money;
    }

    enum CSVHeader {
        id, name, money
    }

    @Test
    public void testCreateCSVFile() throws IOException {
        List<Content> contents = new ArrayList<>();
        contents.add(new Content(1, "zhangsan", new BigDecimal(80.111)));
        contents.add(new Content(2, "lisi", new BigDecimal(95.6)));
        contents.add(new Content(3, "wangwu", new BigDecimal(100)));

        FileWriter writer = new FileWriter("test.csv");
        CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(CSVHeader.class));
        try {
            for (Content content : contents) {
                printer.printRecord(content.id, content.name, content.money.setScale(2, RoundingMode.HALF_UP));
            }
        } finally {
            printer.close(true);
        }
    }

    @Test
    public void testReadCSVFile() throws IOException {
        FileReader reader = new FileReader("test.csv");
        CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.withHeader(CSVHeader.class).withFirstRecordAsHeader());
        while (parser.iterator().hasNext()) {
            CSVRecord record = parser.iterator().next();
            System.out.println("id:" + record.get(CSVHeader.id) + " name:" + record.get(CSVHeader.name) + " money:" + new BigDecimal(record.get(CSVHeader.money)));
        }
        parser.close();
    }
}

```
