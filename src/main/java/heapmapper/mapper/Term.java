package heapmapper.mapper;

sealed interface Term permits Term.NullTerm, Term.IntTerm, Term.VarTerm, Term.FieldTerm {

    record NullTerm()                          implements Term {}
    record IntTerm(int value)                  implements Term {}
    record VarTerm(String name)                implements Term {}
    record FieldTerm(String base, String field) implements Term {}
}
