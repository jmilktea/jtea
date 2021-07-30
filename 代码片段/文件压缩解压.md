## 简介
使用apache commons compress进行压缩和解压，完成最基本功能

## 代码
```
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-compress</artifactId>
        <version>1.19</version>
    </dependency>
        
    @Test
    public void testZip() throws IOException {
        File zipFile = new File("test.zip");
        ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(zipFile);
        zaos.setUseZip64(Zip64Mode.AsNeeded);
        zaos.setEncoding("utf-8");

        //文件1
        File file1 = new File("test1.txt");
        ZipArchiveEntry zipArchiveEntry1 = new ZipArchiveEntry(file1, file1.getName());
        zaos.putArchiveEntry(zipArchiveEntry1);

        InputStream is1 = new BufferedInputStream(new FileInputStream(file1));
        byte[] buffer = new byte[1024];
        int len = -1;
        while ((len = is1.read(buffer)) != -1) {
            //把缓冲区的字节写入到ZipArchiveEntry
            zaos.write(buffer, 0, len);
        }
        is1.close();
        zaos.closeArchiveEntry();

        //文件2
        File file2 = new File("test2.txt");
        ZipArchiveEntry zipArchiveEntry2 = new ZipArchiveEntry(file2, file2.getName());
        zaos.putArchiveEntry(zipArchiveEntry2);

        InputStream is2 = new BufferedInputStream(new FileInputStream(file2));
        byte[] buffer2 = new byte[1024];
        int len2 = -1;
        while ((len2 = is2.read(buffer2)) != -1) {
            //把缓冲区的字节写入到ZipArchiveEntry
            zaos.write(buffer2, 0, len2);
        }
        is2.close();
        zaos.closeArchiveEntry();

        zaos.finish();
        zaos.close();
    }

    @Test
    public void testUnZip() throws IOException {
        File zipFile = new File("test.zip");
        InputStream is = new FileInputStream(zipFile);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(is);
        ArchiveEntry archiveEntry;
        while ((archiveEntry = zais.getNextEntry()) != null) {
            // 获取文件
            File entryFile = new File(archiveEntry.getName());
            OutputStream os = new BufferedOutputStream(new FileOutputStream(entryFile));
            byte[] buffer = new byte[1024];
            int len = -1;
            while ((len = zais.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            os.flush();
            os.close();
        }

        is.close();
        zais.close();
    }
```

使用apache commons compress进行压缩时有一些限制，如没有办法设置压缩包密码，压缩目录等，这里使用zip4j来代替，操作起来更加方便

## 代码
```
    @Test
    public void testZip4jZip() throws ZipException {
        ZipParameters zipParameters = new ZipParameters();
        zipParameters.setCompressionMethod(CompressionMethod.DEFLATE);
        zipParameters.setCompressionLevel(CompressionLevel.NORMAL);
        zipParameters.setEncryptFiles(true);
        zipParameters.setEncryptionMethod(EncryptionMethod.AES);
        zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
        ZipFile zipFile = new ZipFile("zip4j.zip", "password".toCharArray());
        zipFile.addFile("test1.txt", zipParameters);
        zipFile.addFile("test2.txt", zipParameters);
    }

    @Test
    public void testZip4jUnZip() throws ZipException {
        ZipFile zFile = new ZipFile("zip4j.zip");
        if (zFile.isValidZipFile()) {
            if (zFile.isEncrypted()) {
                zFile.setPassword("password".toCharArray());
            }
            zFile.extractAll("zip4junzip");
        }
    }
```
