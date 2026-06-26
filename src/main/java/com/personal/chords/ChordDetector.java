package com.personal.chords;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class ChordDetector {
    private static final int PITCH_CLASS_COUNT = 12;
    private static final int MAX_INTERVAL = 24;
    private static final int MINIMUM_NOTES = 2;
    private static final float MINIMUM_SCORE = 80.0f;
    private static final String[] NOTE_NAMES = {
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };

    private final Map<String, ChordPattern> patterns = new LinkedHashMap<>();
    private final Map<List<Integer>, List<String>> intervalIndex = new HashMap<>();

    public ChordDetector() {
        initializePatterns();
        buildIntervalIndex();
    }

    public ChordResult detect(List<Integer> midiNotes) {
        List<Integer> sortedNotes = uniqueSortedMidiNotes(midiNotes);
        if (sortedNotes.size() < MINIMUM_NOTES) {
            return ChordResult.none(sortedNotes);
        }

        List<Integer> pitchClasses = sortedNotes.stream()
                .map(ChordDetector::pitchClass)
                .collect(Collectors.toList());
        List<Integer> uniquePitchClasses = uniqueSorted(pitchClasses);
        if (uniquePitchClasses.size() < MINIMUM_NOTES) {
            return ChordResult.none(sortedNotes);
        }

        int bassPitchClass = pitchClass(sortedNotes.get(0));
        VoicingType voicingType = classifyVoicing(sortedNotes);
        List<Candidate> candidates = new ArrayList<>();

        for (int potentialRoot : uniquePitchClasses) {
            List<Integer> intervals = intervalsForRoot(uniquePitchClasses, potentialRoot, sortedNotes.size() > 3);
            List<String> candidateTypes = intervalIndex.getOrDefault(intervals, new ArrayList<>(patterns.keySet()));

            for (String chordType : candidateTypes) {
                ChordPattern pattern = patterns.get(chordType);
                float score = computeScore(intervals, pattern, bassPitchClass, potentialRoot, voicingType);
                if (score > MINIMUM_SCORE) {
                    candidates.add(buildCandidate(
                            sortedNotes,
                            pitchClasses,
                            uniquePitchClasses,
                            intervals,
                            potentialRoot,
                            bassPitchClass,
                            chordType,
                            pattern,
                            score,
                            voicingType,
                            false));
                }
            }
        }

        Set<Integer> present = new HashSet<>(uniquePitchClasses);
        for (int virtualRoot = 0; virtualRoot < PITCH_CLASS_COUNT; virtualRoot++) {
            if (present.contains(virtualRoot)) {
                continue;
            }

            List<Integer> intervals = intervalsForRoot(uniquePitchClasses, virtualRoot, sortedNotes.size() > 3);
            List<String> candidateTypes = intervalIndex.getOrDefault(intervals, new ArrayList<>(patterns.keySet()));
            for (String chordType : candidateTypes) {
                ChordPattern pattern = patterns.get(chordType);
                float score = computeScore(intervals, pattern, bassPitchClass, virtualRoot, VoicingType.ROOTLESS);
                if (score > MINIMUM_SCORE) {
                    candidates.add(buildCandidate(
                            sortedNotes,
                            pitchClasses,
                            uniquePitchClasses,
                            intervals,
                            virtualRoot,
                            bassPitchClass,
                            chordType,
                            pattern,
                            score,
                            VoicingType.ROOTLESS,
                            true));
                }
            }
        }

        if (candidates.isEmpty()) {
            return ChordResult.none(sortedNotes);
        }

        candidates.sort(Comparator.comparingDouble((Candidate candidate) -> candidate.score).reversed());
        Candidate best = resolveAmbiguity(candidates, bassPitchClass);
        return new ChordResult(best.chordName, noteNames(sortedNotes), best.noteNames);
    }

    public static String midiNoteName(int midiNote) {
        int pitchClass = pitchClass(midiNote);
        int octave = (midiNote / 12) - 1;
        return NOTE_NAMES[pitchClass] + octave;
    }

    public static List<String> noteNames(List<Integer> midiNotes) {
        return uniqueSortedMidiNotes(midiNotes).stream()
                .map(ChordDetector::midiNoteName)
                .collect(Collectors.toList());
    }

    private Candidate buildCandidate(
            List<Integer> sortedNotes,
            List<Integer> pitchClasses,
            List<Integer> uniquePitchClasses,
            List<Integer> intervals,
            int root,
            int bassPitchClass,
            String chordType,
            ChordPattern pattern,
            float score,
            VoicingType voicingType,
            boolean rootless) {
        Candidate candidate = new Candidate();
        candidate.root = root;
        candidate.chordType = chordType;
        candidate.intervals = intervals;
        candidate.pattern = pattern.intervals;
        candidate.score = score;
        candidate.noteNumbers = sortedNotes;
        candidate.pitchClasses = uniquePitchClasses;
        candidate.noteNames = pitchClasses.stream()
                .map(pc -> NOTE_NAMES[pc])
                .collect(Collectors.toList());

        String chordName = pattern.display.replace("{root}", NOTE_NAMES[root]);
        if (rootless) {
            chordName += "/" + NOTE_NAMES[bassPitchClass];
        } else if (bassPitchClass != root) {
            int bassInterval = intervalBetween(root, bassPitchClass);
            if (pattern.hasInterval(bassInterval)) {
                chordName += "/" + NOTE_NAMES[bassPitchClass];
            } else {
                chordName += "/" + NOTE_NAMES[bassPitchClass];
            }
        }
        candidate.chordName = chordName;
        candidate.voicingType = voicingType;
        return candidate;
    }

    private Candidate resolveAmbiguity(List<Candidate> candidates, int bassPitchClass) {
        if (candidates.size() < 2) {
            return candidates.get(0);
        }

        Candidate top = candidates.get(0);
        Candidate second = candidates.get(1);
        if (top.score - second.score > 40.0f) {
            return top;
        }

        boolean isMajor6vsMinor7 =
                ("major6".equals(top.chordType) && "minor7".equals(second.chordType)) ||
                ("minor7".equals(top.chordType) && "major6".equals(second.chordType));
        if (isMajor6vsMinor7) {
            if (top.root == bassPitchClass) {
                return top;
            }
            if (second.root == bassPitchClass) {
                return second;
            }
            return "major6".equals(top.chordType) ? top : second;
        }

        if ("diminished7".equals(top.chordType) && "diminished7".equals(second.chordType)) {
            if (top.root == bassPitchClass) {
                return top;
            }
            if (second.root == bassPitchClass) {
                return second;
            }
        }

        boolean isMinor6vsMinor =
                ("minor6".equals(top.chordType) && "minor".equals(second.chordType)) ||
                ("minor".equals(top.chordType) && "minor6".equals(second.chordType));
        if (isMinor6vsMinor) {
            Candidate minor6 = "minor6".equals(top.chordType) ? top : second;
            if (minor6.root == bassPitchClass) {
                return minor6;
            }
        }

        return top;
    }

    private float computeScore(
            List<Integer> intervals,
            ChordPattern pattern,
            int bassPitchClass,
            int potentialRoot,
            VoicingType voicingType) {
        if (!intervals.containsAll(pattern.required)) {
            return 0.0f;
        }

        float score = pattern.baseScore;
        Set<Integer> intervalSet = new TreeSet<>(intervals);
        Set<Integer> patternSet = new TreeSet<>(pattern.intervals);

        if (intervalSet.equals(patternSet)) {
            score += 150.0f;
        }

        score += countMatches(intervals, pattern.required) * 30.0f;
        score += countMatches(intervals, pattern.optional) * 10.0f;

        if (bassPitchClass == potentialRoot) {
            score += 25.0f;
        }

        score += countMatches(intervals, pattern.importantIntervals) * 30.0f;

        int matchedCount = 0;
        for (int interval : intervals) {
            if (patternSet.contains(interval)) {
                matchedCount++;
            }
        }
        if (!pattern.intervals.isEmpty()) {
            score += ((float) matchedCount / pattern.intervals.size()) * 80.0f;
        }

        int extraCount = 0;
        for (int interval : intervals) {
            if (!patternSet.contains(interval) && !pattern.optional.contains(interval)) {
                extraCount++;
            }
        }
        score -= extraCount * 4.0f;

        if (voicingType == VoicingType.ROOTLESS) {
            score += 10.0f;
        } else if (voicingType == VoicingType.CLOSE) {
            score += 5.0f;
        }

        if (!intervals.contains(0) && voicingType != VoicingType.ROOTLESS) {
            score -= 40.0f;
        }

        boolean hasThirdOrSus = intervals.contains(2) || intervals.contains(3) ||
                intervals.contains(4) || intervals.contains(5);
        if (!hasThirdOrSus) {
            score -= 25.0f;
        }

        return score;
    }

    private static int countMatches(List<Integer> intervals, List<Integer> expected) {
        int count = 0;
        for (int interval : intervals) {
            if (expected.contains(interval)) {
                count++;
            }
        }
        return count;
    }

    private List<Integer> intervalsForRoot(List<Integer> pitchClasses, int root, boolean includeExtensions) {
        List<Integer> intervals = new ArrayList<>();
        for (int pitchClass : pitchClasses) {
            int interval = intervalBetween(root, pitchClass);
            intervals.add(interval);
            if (includeExtensions && interval != 0) {
                int extended = interval + PITCH_CLASS_COUNT;
                if (extended <= MAX_INTERVAL) {
                    intervals.add(extended);
                }
            }
        }
        return uniqueSorted(intervals);
    }

    private static VoicingType classifyVoicing(List<Integer> midiNotes) {
        if (midiNotes.size() < 2) {
            return VoicingType.UNKNOWN;
        }
        int span = midiNotes.get(midiNotes.size() - 1) - midiNotes.get(0);
        if (span <= 12) {
            return VoicingType.CLOSE;
        }
        if (midiNotes.size() >= 4) {
            List<Integer> adjacentIntervals = new ArrayList<>();
            for (int i = 0; i < midiNotes.size() - 1; i++) {
                adjacentIntervals.add(midiNotes.get(i + 1) - midiNotes.get(i));
            }
            if (adjacentIntervals.size() >= 2 && adjacentIntervals.get(0) > 7) {
                return VoicingType.DROP2;
            }
            if (adjacentIntervals.size() >= 3 && adjacentIntervals.get(1) > 7) {
                return VoicingType.DROP3;
            }
        }
        return VoicingType.OPEN;
    }

    private static int pitchClass(int midiNote) {
        return Math.floorMod(midiNote, PITCH_CLASS_COUNT);
    }

    private static int intervalBetween(int root, int pitchClass) {
        return Math.floorMod(pitchClass - root, PITCH_CLASS_COUNT);
    }

    private static List<Integer> uniqueSortedMidiNotes(List<Integer> midiNotes) {
        if (midiNotes == null || midiNotes.isEmpty()) {
            return new ArrayList<>();
        }
        return uniqueSorted(midiNotes.stream()
                .filter(note -> note >= 0 && note <= 127)
                .collect(Collectors.toList()));
    }

    private static List<Integer> uniqueSorted(List<Integer> values) {
        return new ArrayList<>(new TreeSet<>(values));
    }

    private void addPattern(
            String type,
            List<Integer> intervals,
            int baseScore,
            List<Integer> required,
            List<Integer> optional,
            List<Integer> important,
            String display) {
        patterns.put(type, new ChordPattern(intervals, baseScore, required, optional, important, display));
    }

    private void buildIntervalIndex() {
        intervalIndex.clear();
        for (Map.Entry<String, ChordPattern> entry : patterns.entrySet()) {
            List<Integer> signature = uniqueSorted(entry.getValue().intervals);
            intervalIndex.computeIfAbsent(signature, key -> new ArrayList<>()).add(entry.getKey());
        }
    }

    private void initializePatterns() {
        addPattern("major", ints(0, 4, 7), 100, ints(0, 4, 7), ints(), ints(4, 7), "{root}");
        addPattern("minor", ints(0, 3, 7), 100, ints(0, 3, 7), ints(), ints(3, 7), "{root}m");
        addPattern("diminished", ints(0, 3, 6), 100, ints(0, 3, 6), ints(), ints(3, 6), "{root}dim");
        addPattern("augmented", ints(0, 4, 8), 100, ints(0, 4, 8), ints(), ints(4, 8), "{root}aug");
        addPattern("sus2", ints(0, 2, 7), 95, ints(0, 2, 7), ints(), ints(2, 7), "{root}sus2");
        addPattern("sus4", ints(0, 5, 7), 95, ints(0, 5, 7), ints(), ints(5, 7), "{root}sus4");
        addPattern("power5", ints(0, 7), 80, ints(0, 7), ints(), ints(7), "{root}5");

        addPattern("major7", ints(0, 4, 7, 11), 115, ints(0, 4, 11), ints(7), ints(4, 11), "{root}maj7");
        addPattern("minor7", ints(0, 3, 7, 10), 115, ints(0, 3, 10), ints(7), ints(3, 10), "{root}m7");
        addPattern("dominant7", ints(0, 4, 7, 10), 115, ints(0, 4, 10), ints(7), ints(4, 10), "{root}7");
        addPattern("diminished7", ints(0, 3, 6, 9), 115, ints(0, 3, 6, 9), ints(), ints(3, 6, 9), "{root}dim7");
        addPattern("half-diminished7", ints(0, 3, 6, 10), 115, ints(0, 3, 6, 10), ints(), ints(3, 6, 10), "{root}m7b5");
        addPattern("augmented7", ints(0, 4, 8, 10), 110, ints(0, 4, 8, 10), ints(), ints(4, 8, 10), "{root}aug7");
        addPattern("augmented-major7", ints(0, 4, 8, 11), 110, ints(0, 4, 8, 11), ints(), ints(4, 8, 11), "{root}+maj7");
        addPattern("minor-major7", ints(0, 3, 7, 11), 110, ints(0, 3, 11), ints(7), ints(3, 11), "{root}m(maj7)");
        addPattern("7sus4", ints(0, 5, 7, 10), 108, ints(0, 5, 10), ints(7), ints(5, 10), "{root}7sus4");

        addPattern("major6", ints(0, 4, 7, 9), 105, ints(0, 4, 9), ints(7), ints(4, 9), "{root}6");
        addPattern("minor6", ints(0, 3, 7, 9), 105, ints(0, 3, 9), ints(7), ints(3, 9), "{root}m6");
        addPattern("6/9", ints(0, 4, 7, 9, 14), 110, ints(0, 4, 9, 14), ints(7), ints(4, 9, 14), "{root}6/9");
        addPattern("minor6/9", ints(0, 3, 7, 9, 14), 110, ints(0, 3, 9, 14), ints(7), ints(3, 9, 14), "{root}m6/9");

        addPattern("major9", ints(0, 4, 7, 11, 14), 125, ints(0, 4, 11, 14), ints(7), ints(4, 11, 14), "{root}maj9");
        addPattern("minor9", ints(0, 3, 7, 10, 14), 125, ints(0, 3, 10, 14), ints(7), ints(3, 10, 14), "{root}m9");
        addPattern("dominant9", ints(0, 4, 7, 10, 14), 125, ints(0, 4, 10, 14), ints(7), ints(4, 10, 14), "{root}9");
        addPattern("dominant7b9", ints(0, 4, 7, 10, 13), 120, ints(0, 4, 10, 13), ints(7), ints(4, 10, 13), "{root}7b9");
        addPattern("dominant7#9", ints(0, 4, 7, 10, 15), 120, ints(0, 4, 10, 15), ints(7), ints(4, 10, 15), "{root}7#9");
        addPattern("minor-major9", ints(0, 3, 7, 11, 14), 120, ints(0, 3, 11, 14), ints(7), ints(3, 11, 14), "{root}m(maj9)");

        addPattern("major11", ints(0, 4, 7, 11, 14, 17), 130, ints(0, 4, 11, 14, 17), ints(7), ints(4, 11, 14, 17), "{root}maj11");
        addPattern("minor11", ints(0, 3, 7, 10, 14, 17), 130, ints(0, 3, 10, 14, 17), ints(7), ints(3, 10, 14, 17), "{root}m11");
        addPattern("dominant11", ints(0, 4, 7, 10, 14, 17), 130, ints(0, 4, 10, 14, 17), ints(7), ints(4, 10, 14, 17), "{root}11");
        addPattern("dominant7#11", ints(0, 4, 7, 10, 18), 125, ints(0, 4, 10, 18), ints(7), ints(4, 10, 18), "{root}7#11");
        addPattern("major7#11", ints(0, 4, 7, 11, 18), 125, ints(0, 4, 11, 18), ints(7), ints(4, 11, 18), "{root}maj7#11");
        addPattern("major9#11", ints(0, 4, 7, 11, 14, 18), 130, ints(0, 4, 11, 14, 18), ints(7), ints(4, 11, 14, 18), "{root}maj9#11");
        addPattern("minor11b5", ints(0, 3, 6, 10, 14, 17), 125, ints(0, 3, 6, 10, 14, 17), ints(), ints(3, 6, 10, 14, 17), "{root}m11b5");

        addPattern("major13", ints(0, 4, 7, 11, 14, 21), 135, ints(0, 4, 11, 21), ints(7, 14), ints(4, 11, 21), "{root}maj13");
        addPattern("minor13", ints(0, 3, 7, 10, 14, 21), 135, ints(0, 3, 10, 21), ints(7, 14), ints(3, 10, 21), "{root}m13");
        addPattern("dominant13", ints(0, 4, 7, 10, 14, 21), 135, ints(0, 4, 10, 21), ints(7, 14), ints(4, 10, 21), "{root}13");
        addPattern("dominant13#11", ints(0, 4, 7, 10, 18, 21), 135, ints(0, 4, 10, 18, 21), ints(7), ints(4, 10, 18, 21), "{root}13#11");
        addPattern("dominant7b13", ints(0, 4, 7, 10, 20), 125, ints(0, 4, 10, 20), ints(7), ints(4, 10, 20), "{root}7b13");
        addPattern("dominant13b9", ints(0, 4, 7, 10, 13, 21), 130, ints(0, 4, 10, 13, 21), ints(7), ints(4, 10, 13, 21), "{root}13b9");
        addPattern("dominant13#9", ints(0, 4, 7, 10, 15, 21), 130, ints(0, 4, 10, 15, 21), ints(7), ints(4, 10, 15, 21), "{root}13#9");

        addPattern("dominant7b5", ints(0, 4, 6, 10), 118, ints(0, 4, 6, 10), ints(), ints(4, 6, 10), "{root}7b5");
        addPattern("dominant7#5", ints(0, 4, 8, 10), 118, ints(0, 4, 8, 10), ints(), ints(4, 8, 10), "{root}7#5");
        addPattern("dominant7b5b9", ints(0, 4, 6, 10, 13), 122, ints(0, 4, 6, 10, 13), ints(), ints(4, 6, 10, 13), "{root}7b5b9");
        addPattern("dominant7#5b9", ints(0, 4, 8, 10, 13), 122, ints(0, 4, 8, 10, 13), ints(), ints(4, 8, 10, 13), "{root}7#5b9");
        addPattern("dominant7b5#9", ints(0, 4, 6, 10, 15), 122, ints(0, 4, 6, 10, 15), ints(), ints(4, 6, 10, 15), "{root}7b5#9");
        addPattern("dominant7#5#9", ints(0, 4, 8, 10, 15), 122, ints(0, 4, 8, 10, 15), ints(), ints(4, 8, 10, 15), "{root}7#5#9");
        addPattern("altered", ints(0, 4, 6, 10, 13), 120, ints(0, 4, 10), ints(6, 8, 13, 15), ints(4, 10), "{root}7alt");
        addPattern("dominant7#5#9b13", ints(0, 4, 8, 10, 15, 20), 128, ints(0, 4, 8, 10, 15, 20), ints(), ints(4, 8, 10, 15, 20), "{root}7#5#9b13");
        addPattern("dominant9#11", ints(0, 4, 7, 10, 14, 18), 130, ints(0, 4, 10, 14, 18), ints(7), ints(4, 10, 14, 18), "{root}9#11");
        addPattern("dominant9b13", ints(0, 4, 7, 10, 14, 20), 130, ints(0, 4, 10, 14, 20), ints(7), ints(4, 10, 14, 20), "{root}9b13");
        addPattern("dominant7#9#11", ints(0, 4, 7, 10, 15, 18), 128, ints(0, 4, 10, 15, 18), ints(7), ints(4, 10, 15, 18), "{root}7#9#11");
        addPattern("dominant7b9#11", ints(0, 4, 7, 10, 13, 18), 128, ints(0, 4, 10, 13, 18), ints(7), ints(4, 10, 13, 18), "{root}7b9#11");
        addPattern("dominant7b9b13", ints(0, 4, 7, 10, 13, 20), 128, ints(0, 4, 10, 13, 20), ints(7), ints(4, 10, 13, 20), "{root}7b9b13");
        addPattern("dominant7#9b13", ints(0, 4, 7, 10, 15, 20), 128, ints(0, 4, 10, 15, 20), ints(7), ints(4, 10, 15, 20), "{root}7#9b13");

        addPattern("add9", ints(0, 4, 7, 14), 105, ints(0, 4, 7, 14), ints(), ints(4, 7, 14), "{root}add9");
        addPattern("minor-add9", ints(0, 3, 7, 14), 105, ints(0, 3, 7, 14), ints(), ints(3, 7, 14), "{root}m(add9)");
        addPattern("add11", ints(0, 4, 7, 17), 100, ints(0, 4, 7, 17), ints(), ints(4, 7, 17), "{root}add11");
        addPattern("add#11", ints(0, 4, 7, 18), 100, ints(0, 4, 7, 18), ints(), ints(4, 7, 18), "{root}add#11");
        addPattern("major7#5", ints(0, 4, 8, 11), 115, ints(0, 4, 8, 11), ints(), ints(4, 8, 11), "{root}maj7#5");
        addPattern("minor7b5", ints(0, 3, 6, 10), 115, ints(0, 3, 6, 10), ints(), ints(3, 6, 10), "{root}m7b5");
        addPattern("quartal", ints(0, 5, 10), 90, ints(0, 5, 10), ints(), ints(5, 10), "{root}quartal");
        addPattern("quartal-7", ints(0, 5, 10, 15), 95, ints(0, 5, 10, 15), ints(), ints(5, 10, 15), "{root}quartal7");
    }

    private static List<Integer> ints(int... values) {
        return Arrays.stream(values).boxed().collect(Collectors.toList());
    }

    public static final class ChordResult {
        private final String chordName;
        private final List<String> midiNoteNames;
        private final List<String> pitchClassNames;

        private ChordResult(String chordName, List<String> midiNoteNames, List<String> pitchClassNames) {
            this.chordName = chordName;
            this.midiNoteNames = List.copyOf(midiNoteNames);
            this.pitchClassNames = List.copyOf(pitchClassNames);
        }

        static ChordResult none(List<Integer> midiNotes) {
            return new ChordResult("N.C.", noteNames(midiNotes), List.of());
        }

        public String chordName() {
            return chordName;
        }

        public List<String> midiNoteNames() {
            return midiNoteNames;
        }

        public List<String> pitchClassNames() {
            return pitchClassNames;
        }
    }

    private static final class ChordPattern {
        private final List<Integer> intervals;
        private final int baseScore;
        private final List<Integer> required;
        private final List<Integer> optional;
        private final List<Integer> importantIntervals;
        private final String display;

        private ChordPattern(
                List<Integer> intervals,
                int baseScore,
                List<Integer> required,
                List<Integer> optional,
                List<Integer> importantIntervals,
                String display) {
            this.intervals = List.copyOf(intervals);
            this.baseScore = baseScore;
            this.required = List.copyOf(required);
            this.optional = List.copyOf(optional);
            this.importantIntervals = List.copyOf(importantIntervals);
            this.display = display;
        }

        private boolean hasInterval(int interval) {
            return intervals.contains(interval);
        }
    }

    private static final class Candidate {
        private int root;
        private String chordType;
        private String chordName;
        private List<Integer> intervals;
        private List<Integer> pattern;
        private float score;
        private VoicingType voicingType;
        private List<Integer> noteNumbers;
        private List<Integer> pitchClasses;
        private List<String> noteNames;
    }

    private enum VoicingType {
        CLOSE,
        OPEN,
        ROOTLESS,
        DROP2,
        DROP3,
        UNKNOWN
    }
}
