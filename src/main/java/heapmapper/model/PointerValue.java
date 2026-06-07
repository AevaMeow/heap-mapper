package heapmapper.model;

public sealed interface PointerValue
        permits PointerValue.NullPtr, PointerValue.KnownPtr, PointerValue.UnknownPtr {

    record NullPtr() implements PointerValue {}

    record KnownPtr(int address) implements PointerValue {}

    record UnknownPtr() implements PointerValue {}
}
