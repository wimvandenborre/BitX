## Bitwig API Reference

The project includes a local copy of the Bitwig API reference at `src/main/resources/bitwigapi/BitwigAPI25.txt`.

How to use:

- The full API text is available as a resource at runtime for quick lookups.
- A generator script `tools/generate_bitwig_stubs.py` can create minimal IDE-friendly Java stubs under `src/generated-sources/bitwigapi` for compile-time reference.

Generate stubs and build:

```bash
python3 tools/generate_bitwig_stubs.py
mvn generate-sources
mvn -DskipTests package
```

There's also a simple lookup utility at `src/main/java/com/personal/bitwig/BitwigApiLookup.java` to search the API text at runtime.
