package io.github.nidaba.skyforge.reference;

import java.io.IOException;
import java.nio.file.Path;

/** Regenerates and verifies the canonical v0.1 fixed-seed corpus. */
public final class FixedSeedCorpusCli {
    private FixedSeedCorpusCli() {}

    /** Generates the corpus, verifies all golden hashes, and reports review artifacts. */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length > 2) {
            throw new IllegalArgumentException(
                    "usage: FixedSeedCorpusCli [output-directory] [--emit-without-verification]");
        }
        Path output = arguments.length >= 1
                ? Path.of(arguments[0])
                : Path.of("build", "evidence", FixedSeedReferenceCorpus.CORPUS_ID);
        boolean verify = arguments.length < 2;
        if (arguments.length == 2 && !"--emit-without-verification".equals(arguments[1])) {
            throw new IllegalArgumentException("unknown option: " + arguments[1]);
        }
        String version = System.getProperty("skyforge.version", "development");
        FixedSeedCorpusGenerator.Result result = new FixedSeedCorpusGenerator().generate(output, version);
        if (verify) {
            int verified = FixedSeedCorpusVerifier.verify(output);
            System.out.println("verified " + verified + " canonical fixed-seed artifacts");
        } else {
            System.out.println("verification skipped while accepting a new versioned corpus");
        }
        for (FixedSeedCorpusGenerator.MemberResult member : result.members()) {
            System.out.printf(
                    "%s: %.3f s, %.0f samples/s%n",
                    member.member().id(),
                    member.benchmark().wallTimeNanoseconds() / 1_000_000_000.0,
                    member.benchmark().samplesPerSecond());
        }
        System.out.println(result.gallery().toAbsolutePath());
        System.out.println(result.benchmark().toAbsolutePath());
    }
}
