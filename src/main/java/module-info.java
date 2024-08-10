module jafar.main {
    requires it.unimi.dsi.fastutil;
    requires org.slf4j;
    requires org.objectweb.asm;
    requires org.checkerframework.checker.qual;
    requires org.openjdk.jmc.flightrecorder;
    requires org.openjdk.jmc.common;

    exports io.jafar.parser.api;
    exports io.jafar.parser.api.types;
}