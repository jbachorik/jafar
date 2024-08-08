# jafar
Experimental, incomplete JFR parser

Very much a work in progress. 
The goal is to be able to parse JFR files and extract the event data in programmatic way with the least effort possible.

## Tl;DR
Allow quickly wiring JFR with interface based handlers using bytecode generation.
I was nerdsniped by [@nitsanw](https://github.com/nitsanw) and quickly thrown together this more or less a PoC.

## Usage
The main idea is to define a handling interface which corresponds to a JFR event type. The linking is done via `@JfrType` 
annotation. For convenience, there is a `JfrEvent` interface which can be extended to define the event handling interface.

The interface methods should correspond to the fields of the JFR event. The method names should be the same as the field names.
If the field name is not a valid Java identifier, the method will be linked with the field via `@JfrField` annotation.
The interface can have methods excluded from linking with the JFR types - by annotating such methods with `@JfrIgnore`.

```java

@JfrType("custom.MyEvent")
public interface MyEvent extends JfrEvent {}

JafarParser parser = JafarParser.open("path_to_jfr.jfr");
parser.handle(MyEvent.class, event -> {
    System.out.println(event.startTime());
    System.out.println(event.eventThread().javaName());
});
parser.handle(MyEvent.class, event -> {
    // do something else
});
parser.run();
```

This short program will parse the recording and call the `handle` method for each `custom.MyEvent` event.
The number of handlers per type is not limited, they all will be executed sequentially.
With the handlers known beforehand, the parser can safely skip all unreachable events and types, massively saving on the parsing time.
