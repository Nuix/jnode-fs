# Agent guidance for jnode-fs

## Purpose

This library reads file systems for forensic investigations. Investigators use it on damaged volumes, partly
overwritten volumes and deleted files. Data that the library can recover is more important than strict conformance to
the file system specification.

Apply these rules when you change a reader:

- Return the data that you can read. Do not reject a whole file, stream or record because one part of it is damaged.
- Keep the attributes, runs and chunks that decode before a bad one. Do not discard them.
- Flag suspect data through the API (for example `FileRecord.isAttributeListIncomplete()` or
  `FileRecord.getCorruptCompressionUnits(...)`). The caller decides what to trust.
- Throw only when the result would be wrong without a sign that it is wrong. An example is all-zero data in place of
  WOF compressed content.
- Do not make a read fail that worked before, unless the old result was wrong data.

`NTFSCompressedDataRunTriage4924Test` shows the intent. It decodes a compression unit from a point after the start of
its first chunk, and it asserts that the decoder still returns the text that follows.

## Tests

Use real disk images for tests where you can. Synthetic records and hand-built byte arrays do not show how real
volumes are damaged. Use synthetic data only for a case that no real image contains, or to isolate one condition.

- Put only raw volume images, compressed with gzip, in `src/test/resources`. Convert E01, VHDX and similar formats
  to raw first.
- Record the source and licence of each fixture in the `SOURCE.md` file in the same directory.
- Do not add fixtures under a licence that prevents their redistribution, for example GPL-3.0.
- A fixture that is too large to commit can be read from a path in a system property. The test must then skip when
  the property is not set. `NTFSLogFileFixtureTest.testStolenSzechuan` is an example.

## Build

Build with JDK 17. Lombok fails to compile on later JDKs.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn test
```
