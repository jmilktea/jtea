## 简介
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
