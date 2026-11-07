package com.codejudge.model.enums;

import java.util.Arrays;

/**
 * 支持的编程语言。
 * <p>
 * 只支持 Java 和 Python 是刻意的：它们分别代表"编译型"和"解释型"两条完全不同的判题流程——
 * 编译型语言有独立的编译阶段（可能产生 CE，且编译耗时不计入运行时间），
 * 解释型语言没有。两种语言足以验证抽象是否站得住，加第三种只是复制粘贴。
 */
public enum Language {

    JAVA("java", "Main.java", true),
    PYTHON("python", "main.py", false);

    /** 落库和对外传输用的标识 */
    private final String code;

    /** 在沙箱工作目录里的文件名 */
    private final String fileName;

    /** 是否需要编译 */
    private final boolean compiled;

    Language(String code, String fileName, boolean compiled) {
        this.code = code;
        this.fileName = fileName;
        this.compiled = compiled;
    }

    public String getCode() {
        return code;
    }

    public String getFileName() {
        return fileName;
    }

    public boolean isCompiled() {
        return compiled;
    }

    /**
     * 按 code 解析，无法识别时返回 null（由调用方转成参数错误）。
     */
    public static Language of(String code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(l -> l.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }
}
