package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Value;

public class SystemConstants {
    @Value("${hm.upload.path}")
    public static final String IMAGE_UPLOAD_DIR = "D:\\hmdp-Java\\upload\\imgs\\";
    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;
}
