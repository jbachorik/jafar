# jafar
Experimental, incomplete JFR parser

Very much a work in progress. 
The goal is to be able to parse JFR files and extract the event data in programmatic way with the least effort possible.

## Requirements
Java 21 (mostly just because I wanted to try the pattern matching)

## Tl;DR
Allow quickly wiring JFR with interface based handlers using bytecode generation.
I was nerdsniped by [@nitsanw](https://github.com/nitsanw) and quickly thrown together this more or less a PoC.

The parser is pretty fast, actually. You can try the demo app which will extract all the `jdk.ExecutionSample` events,
count the number of samples and calculate the sum of the associated thread ids (useful, right?). On Mac M1 and ~600MiB
JFR this takes around 1 second as compared to cca. 7 seconds using JMC parser. The JDK `jfr` tool will run out of memory,
but to be fair it is trying to print the full content of each event.

After the project is build via `./gradlew shadowJar` you can run the demo app with:
```shell
# The Jafar parser
java -jar demo/build/libs/demo-all.jar [jafar|jmc|jfr] path_to_jfr.jfr
```

## Usage
The main idea is to define a handling interface which corresponds to a JFR event type. The linking is done via `@JfrType` 
annotation. For convenience, there is a `JfrEvent` interface which can be extended to define the event handling interface.

The interface methods should correspond to the fields of the JFR event. The method names should be the same as the field names.
If the field name is not a valid Java identifier, the method will be linked with the field via `@JfrField` annotation.
The interface can have methods excluded from linking with the JFR types - by annotating such methods with `@JfrIgnore`.

```java

@JfrType("custom.MyEvent")
public interface MyEvent extends JfrEvent {
  String myfield();
}

try (JafarParser parser = JafarParser.open("path_to_jfr.jfr")) {}
    // registering a handler will return a cookie which can be used to deregister the same handler
    var cookie = parser.handle(MyEvent.class, event -> {
        System.out.println(event.startTime());
        System.out.println(event.eventThread().javaName());
        System.out.println(event.myfield());
    });
    parser.handle(MyEvent.class, event -> {
        // do something else
    });
    parser.run();
    
    cookie.destroy(parser);
    // this time only the second handler will be called
    parser.run();
}

```

This short program will parse the recording and call the `handle` method for each `custom.MyEvent` event.
The number of handlers per type is not limited, they all will be executed sequentially.
With the handlers known beforehand, the parser can safely skip all unreachable events and types, massively saving on the parsing time.
