module jafar.main {
    requires it.unimi.dsi.fastutil;
    requires org.slf4j;
    requires org.objectweb.asm;
    requires org.checkerframework.checker.qual;

    exports io.jafar.parser.api;
    exports io.jafar.parser.api.types;
}