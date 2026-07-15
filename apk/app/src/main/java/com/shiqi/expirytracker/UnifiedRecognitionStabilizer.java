package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class UnifiedRecognitionStabilizer {
    private static final int DEFAULT_MAX_FRAMES = 48;
    private static final int DEFAULT_DATE_MIN_VOTES = 3;
    private static final int BARCODE_LOCK_VOTES = 2;
    private static final int PACKAGING_NAME_LOCK_VOTES = 3;
    private static final int MAX_PACKAGING_CANDIDATES = 3;
    private static final double MIN_REPEATED_NAME_SCORE = 70d;
    private static final double MIN_SINGLE_STRONG_NAME_SCORE = 100d;

    private final int maxFrames;
    private final int dateMinVotes;
    private final List<Frame> frames = new ArrayList<Frame>();
    private String lockedBarcode = "";
    private String lockedPackagingName = "";
    private DateOcrFrameVoter.VoteResult stableDateVote;
    private DateOcrFrameVoter.VoteResult latestDateVote;
    private String latestRawText = "";

    UnifiedRecognitionStabilizer() {
        this(DEFAULT_MAX_FRAMES, DEFAULT_DATE_MIN_VOTES);
    }

    UnifiedRecognitionStabilizer(int maxFrames, int dateMinVotes) {
        this.maxFrames = Math.max(2, maxFrames);
        this.dateMinVotes = Math.max(1, dateMinVotes);
    }

    Snapshot addFrame(
            String rawBarcode,
            DateOcrParser.Result ocrResult,
            String rawText,
            boolean singleFrameConfirmation
    ) {
        return addFrame(
                rawBarcode,
                ocrResult,
                rawText,
                candidatesFromRawText(rawText),
                singleFrameConfirmation
        );
    }

    Snapshot addFrame(
            String rawBarcode,
            DateOcrParser.Result ocrResult,
            String rawText,
            List<PackagingTextAnalyzer.Candidate> packagingCandidates,
            boolean singleFrameConfirmation
    ) {
        String barcode = BarcodeUtils.extractProductCode(rawBarcode);
        if (!BarcodeUtils.isSupportedProductCode(barcode)) {
            barcode = "";
        }

        String cleanedRawText = FoodItem.cleanText(rawText);
        List<PackagingTextAnalyzer.Candidate> allCandidates =
                new ArrayList<PackagingTextAnalyzer.Candidate>();
        if (packagingCandidates != null) {
            allCandidates.addAll(packagingCandidates);
        }
        allCandidates.addAll(candidatesFromRawText(cleanedRawText));
        List<PackagingTextAnalyzer.Candidate> normalizedCandidates =
                normalizeFrameCandidates(allCandidates);
        Frame frame = new Frame(barcode, normalizedCandidates, ocrResult, cleanedRawText);
        frames.add(frame);
        while (frames.size() > maxFrames) {
            frames.remove(0);
        }

        if (frame.rawText.length() > 0) {
            latestRawText = frame.rawText;
        }
        updateBarcodeLock(barcode, singleFrameConfirmation);
        updatePackagingNameLock(singleFrameConfirmation);
        updateDateVote(singleFrameConfirmation);
        return snapshot();
    }

    Snapshot snapshot() {
        List<PackagingTextAnalyzer.Candidate> rankedCandidates = rankPackagingCandidates();
        return new Snapshot(
                frames.size(),
                lockedBarcode,
                barcodeVotes(lockedBarcode),
                lockedPackagingName,
                packagingNameVotes(lockedPackagingName),
                rankedCandidates,
                stableDateVote,
                latestDateVote,
                latestRawText
        );
    }

    Snapshot promoteDirectDatePairForConfirmation(DateOcrParser.Result result) {
        if (stableDateVote != null
                && stableDateVote.productionDate != null
                && stableDateVote.expiryDate != null
                && !stableDateVote.hasConflict) {
            return snapshot();
        }
        if (result == null || result.productionDates.isEmpty() || result.expiryDates.isEmpty()) {
            return snapshot();
        }
        DateOcrFrameVoter.VoteResult direct = DateOcrFrameVoter.vote(
                Collections.singletonList(result),
                1
        );
        if (isReliableDirectDatePair(direct)) {
            latestDateVote = direct;
            stableDateVote = direct;
        }
        return snapshot();
    }

    static boolean isReliableDirectDatePair(DateOcrFrameVoter.VoteResult direct) {
        if (direct == null
                || direct.productionDate == null
                || direct.expiryDate == null
                || direct.hasConflict
                || direct.productionDate.value.compareTo(DateRules.getTodayString()) > 0) {
            return false;
        }
        boolean repeatedEvidence = direct.productionDate.votes >= 2
                && direct.expiryDate.votes >= 2;
        boolean highConfidenceEvidence = direct.productionDate.confidence >= 0.70d
                && direct.expiryDate.confidence >= 0.70d;
        boolean anchoredMixedEvidence = (direct.productionDate.votes >= 2
                && direct.expiryDate.confidence >= 0.65d)
                || (direct.expiryDate.votes >= 2
                && direct.productionDate.confidence >= 0.65d);
        if (!repeatedEvidence && !highConfidenceEvidence && !anchoredMixedEvidence) {
            return false;
        }
        int spanDays = DateRules.daysBetween(
                direct.productionDate.value,
                direct.expiryDate.value
        );
        return spanDays >= 0 && spanDays <= (366 * 5);
    }

    void reset() {
        frames.clear();
        lockedBarcode = "";
        lockedPackagingName = "";
        stableDateVote = null;
        latestDateVote = null;
        latestRawText = "";
    }

    private void updateBarcodeLock(String barcode, boolean singleFrameConfirmation) {
        if (lockedBarcode.length() > 0) {
            return;
        }
        if (!BarcodeUtils.isSupportedProductCode(barcode)) {
            return;
        }
        if (singleFrameConfirmation || barcodeVotes(barcode) >= BARCODE_LOCK_VOTES) {
            lockedBarcode = barcode;
        }
    }

    private void updateDateVote(boolean singleFrameConfirmation) {
        List<DateOcrParser.Result> ocrFrames = new ArrayList<DateOcrParser.Result>();
        for (Frame frame : frames) {
            if (frame.ocrResult != null && frame.ocrResult.hasAnyCandidate()) {
                ocrFrames.add(frame.ocrResult);
            }
        }
        latestDateVote = DateOcrFrameVoter.vote(
                ocrFrames,
                singleFrameConfirmation ? 1 : dateMinVotes
        );
        if (singleFrameConfirmation && latestDateVote.hasStableCandidate()) {
            stableDateVote = latestDateVote;
        } else if (latestDateVote.readyForUserConfirmation()) {
            if (stableDateVote == null
                    || sameDateVote(stableDateVote, latestDateVote)
                    || dateVoteQuality(latestDateVote) > dateVoteQuality(stableDateVote)
                    || dateVoteStrength(latestDateVote) >= dateVoteStrength(stableDateVote) + 2) {
                stableDateVote = latestDateVote;
            }
        }
    }

    private static boolean sameDateVote(
            DateOcrFrameVoter.VoteResult first,
            DateOcrFrameVoter.VoteResult second
    ) {
        return sameDate(first.productionDate, second.productionDate)
                && sameDate(first.expiryDate, second.expiryDate)
                && sameDate(first.calculatedExpiryDate, second.calculatedExpiryDate)
                && sameShelfLife(first.shelfLife, second.shelfLife);
    }

    private static boolean sameDate(
            DateOcrFrameVoter.StableDate first,
            DateOcrFrameVoter.StableDate second
    ) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.value.equals(second.value) && first.type.equals(second.type);
    }

    private static boolean sameShelfLife(
            DateOcrFrameVoter.StableShelfLife first,
            DateOcrFrameVoter.StableShelfLife second
    ) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.value == second.value && first.unit.equals(second.unit);
    }

    private static int dateVoteStrength(DateOcrFrameVoter.VoteResult vote) {
        if (vote == null) {
            return 0;
        }
        int strength = 0;
        strength += vote.productionDate == null ? 0 : vote.productionDate.votes;
        strength += vote.expiryDate == null ? 0 : vote.expiryDate.votes;
        strength += vote.calculatedExpiryDate == null ? 0 : vote.calculatedExpiryDate.votes;
        strength += vote.shelfLife == null ? 0 : vote.shelfLife.votes;
        return strength;
    }

    private static int dateVoteQuality(DateOcrFrameVoter.VoteResult vote) {
        if (vote == null) {
            return 0;
        }
        int quality = 0;
        quality += vote.productionDate != null && !vote.productionDate.weakHint ? 4 : 0;
        quality += vote.expiryDate != null && !vote.expiryDate.weakHint ? 4 : 0;
        quality += vote.shelfLife != null ? 2 : 0;
        if (vote.productionDate != null && vote.expiryDate != null) {
            int order = vote.productionDate.value.compareTo(vote.expiryDate.value);
            quality += order < 0 ? 5 : -5;
        }
        return quality;
    }

    private void updatePackagingNameLock(boolean singleFrameConfirmation) {
        List<PackagingTextAnalyzer.Candidate> ranked = rankPackagingCandidates();
        if (ranked.isEmpty()) {
            return;
        }
        if (singleFrameConfirmation) {
            lockedPackagingName = ranked.get(0).text;
            return;
        }
        if (lockedPackagingName.length() > 0) {
            PackagingTextAnalyzer.Candidate leader = ranked.get(0);
            PackagingTextAnalyzer.Candidate locked = findRankedCandidate(ranked, lockedPackagingName);
            int lockedVotes = locked == null ? packagingNameVotes(lockedPackagingName) : locked.votes;
            double lockedScore = locked == null ? 0d : locked.score;
            if (!RecognitionTextCleaner.productNamesSimilar(lockedPackagingName, leader.text)
                    && leader.votes >= PACKAGING_NAME_LOCK_VOTES
                    && (leader.votes >= lockedVotes + 2
                    || (lockedScore > 0d && leader.score >= lockedScore * 1.30d))) {
                lockedPackagingName = leader.text;
            }
            return;
        }
        for (PackagingTextAnalyzer.Candidate candidate : ranked) {
            if (candidate.votes >= PACKAGING_NAME_LOCK_VOTES
                    && !RecognitionTextCleaner.isLikelyMarketingSlogan(candidate.text)) {
                lockedPackagingName = candidate.text;
                return;
            }
        }
    }

    private static PackagingTextAnalyzer.Candidate findRankedCandidate(
            List<PackagingTextAnalyzer.Candidate> ranked,
            String name
    ) {
        for (PackagingTextAnalyzer.Candidate candidate : ranked) {
            if (RecognitionTextCleaner.productNamesSimilar(name, candidate.text)) {
                return candidate;
            }
        }
        return null;
    }

    private int barcodeVotes(String barcode) {
        String code = BarcodeUtils.digitsOnly(barcode);
        if (code.length() == 0) {
            return 0;
        }
        int votes = 0;
        for (Frame frame : frames) {
            if (code.equals(frame.barcode)) {
                votes++;
            }
        }
        return votes;
    }

    private int packagingNameVotes(String packagingName) {
        String name = FoodItem.cleanText(packagingName);
        if (name.length() == 0) {
            return 0;
        }
        int votes = 0;
        for (Frame frame : frames) {
            for (PackagingTextAnalyzer.Candidate candidate : frame.packagingCandidates) {
                if (RecognitionTextCleaner.productNamesSimilar(name, candidate.text)) {
                    votes++;
                    break;
                }
            }
        }
        return votes;
    }

    private List<PackagingTextAnalyzer.Candidate> rankPackagingCandidates() {
        List<PackagingAggregate> aggregates = new ArrayList<PackagingAggregate>();
        int frameCount = frames.size();
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            Frame frame = frames.get(frameIndex);
            double recencyWeight = 0.70d + (0.30d * (frameIndex + 1) / Math.max(1, frameCount));
            for (PackagingTextAnalyzer.Candidate candidate : frame.packagingCandidates) {
                PackagingAggregate aggregate = findAggregate(aggregates, candidate.text);
                if (aggregate == null) {
                    aggregate = new PackagingAggregate(candidate.text);
                    aggregates.add(aggregate);
                }
                aggregate.add(candidate, recencyWeight);
            }
        }

        List<PackagingTextAnalyzer.Candidate> ranked = new ArrayList<PackagingTextAnalyzer.Candidate>();
        for (PackagingAggregate aggregate : aggregates) {
            PackagingTextAnalyzer.Candidate candidate = aggregate.toCandidate();
            if (isDisplayEligible(candidate)) {
                ranked.add(candidate);
            }
        }
        Collections.sort(ranked, new Comparator<PackagingTextAnalyzer.Candidate>() {
            @Override
            public int compare(PackagingTextAnalyzer.Candidate left, PackagingTextAnalyzer.Candidate right) {
                int scoreOrder = Double.compare(right.score, left.score);
                if (scoreOrder != 0) {
                    return scoreOrder;
                }
                int voteOrder = Integer.compare(right.votes, left.votes);
                if (voteOrder != 0) {
                    return voteOrder;
                }
                return left.text.compareTo(right.text);
            }
        });
        if (ranked.size() > MAX_PACKAGING_CANDIDATES) {
            ranked = new ArrayList<PackagingTextAnalyzer.Candidate>(ranked.subList(0, MAX_PACKAGING_CANDIDATES));
        }
        return Collections.unmodifiableList(ranked);
    }

    private static PackagingAggregate findAggregate(List<PackagingAggregate> aggregates, String text) {
        PackagingAggregate best = null;
        double bestSimilarity = 0d;
        for (PackagingAggregate aggregate : aggregates) {
            double similarity = RecognitionTextCleaner.productNameSimilarity(aggregate.text, text);
            if (similarity >= 0.72d && similarity > bestSimilarity) {
                best = aggregate;
                bestSimilarity = similarity;
            }
        }
        return best;
    }

    private static List<PackagingTextAnalyzer.Candidate> candidatesFromRawText(String rawText) {
        String cleanedRawText = FoodItem.cleanText(rawText);
        StringBuilder unlabeledText = new StringBuilder();
        for (String line : cleanedRawText.split("\\r?\\n")) {
            String labeledName = RecognitionTextCleaner.intelligentProductNameCandidate(
                    RecognitionTextCleaner.extractLabeledProductName(line)
            );
            int labeledScore = RecognitionTextCleaner.productNameScore(labeledName);
            if (labeledScore > 0
                    && RecognitionTextCleaner.isHighConfidenceLabeledProductName(labeledName)) {
                return Collections.singletonList(new PackagingTextAnalyzer.Candidate(
                        labeledName,
                        labeledScore + 180d,
                        1,
                        Collections.singletonList(FoodItem.cleanText(line))
                ));
            }
            if (!RecognitionTextCleaner.hasProductNameLabel(line)) {
                if (unlabeledText.length() > 0) {
                    unlabeledText.append('\n');
                }
                unlabeledText.append(line);
            }
        }
        String packagingName = RecognitionTextCleaner.intelligentProductNameCandidate(
                RecognitionTextCleaner.extractProductNameFromOcr(unlabeledText.toString())
        );
        int score = RecognitionTextCleaner.productNameScore(packagingName);
        if (score <= 0 || !RecognitionTextCleaner.isHighConfidenceFoodProductName(packagingName)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new PackagingTextAnalyzer.Candidate(packagingName, score + 85d));
    }

    private static List<PackagingTextAnalyzer.Candidate> normalizeFrameCandidates(
            List<PackagingTextAnalyzer.Candidate> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        List<PackagingTextAnalyzer.Candidate> normalized = new ArrayList<PackagingTextAnalyzer.Candidate>();
        for (PackagingTextAnalyzer.Candidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String text = RecognitionTextCleaner.intelligentProductNameCandidate(candidate.text);
            int lexicalScore = RecognitionTextCleaner.productNameScore(text);
            boolean labeledEvidence = hasProductNameLabelEvidence(candidate.evidence);
            if (lexicalScore <= 0
                    || (labeledEvidence
                    ? !RecognitionTextCleaner.isHighConfidenceLabeledProductName(text)
                    : !RecognitionTextCleaner.isHighConfidenceFoodProductName(text))) {
                continue;
            }
            PackagingTextAnalyzer.Candidate safeCandidate = new PackagingTextAnalyzer.Candidate(
                    text,
                    Math.max(candidate.score, lexicalScore),
                    1,
                    candidate.evidence
            );
            int similarIndex = similarCandidateIndex(normalized, text);
            if (similarIndex < 0) {
                normalized.add(safeCandidate);
            } else {
                PackagingTextAnalyzer.Candidate existing = normalized.get(similarIndex);
                boolean existingCanonical = RecognitionTextCleaner.isCanonicalFoodName(existing.text);
                boolean incomingCanonical = RecognitionTextCleaner.isCanonicalFoodName(text);
                if (existingCanonical && !incomingCanonical) {
                    continue;
                }
                boolean meaningfullyLonger = text.length() >= existing.text.length() + 2
                        && safeCandidate.score >= existing.score * 0.70d;
                if ((incomingCanonical && !existingCanonical)
                        || safeCandidate.score > existing.score
                        || meaningfullyLonger) {
                    normalized.set(similarIndex, new PackagingTextAnalyzer.Candidate(
                            meaningfullyLonger ? text : safeCandidate.text,
                            Math.max(existing.score, safeCandidate.score),
                            1,
                            safeCandidate.evidence
                    ));
                }
            }
        }
        Collections.sort(normalized, new Comparator<PackagingTextAnalyzer.Candidate>() {
            @Override
            public int compare(PackagingTextAnalyzer.Candidate left, PackagingTextAnalyzer.Candidate right) {
                return Double.compare(right.score, left.score);
            }
        });
        if (normalized.size() > MAX_PACKAGING_CANDIDATES) {
            normalized = new ArrayList<PackagingTextAnalyzer.Candidate>(
                    normalized.subList(0, MAX_PACKAGING_CANDIDATES)
            );
        }
        return Collections.unmodifiableList(normalized);
    }

    private static boolean isDisplayEligible(PackagingTextAnalyzer.Candidate candidate) {
        if (candidate == null || candidate.text.length() == 0) {
            return false;
        }
        boolean labeledEvidence = hasProductNameLabelEvidence(candidate.evidence);
        if (labeledEvidence) {
            return RecognitionTextCleaner.isHighConfidenceLabeledProductName(candidate.text)
                    && candidate.score >= MIN_REPEATED_NAME_SCORE;
        }
        boolean foodName = RecognitionTextCleaner.isHighConfidenceFoodProductName(candidate.text);
        if (!foodName) {
            return false;
        }
        if (candidate.votes >= 2) {
            return candidate.score >= MIN_REPEATED_NAME_SCORE;
        }
        return foodName && candidate.score >= MIN_SINGLE_STRONG_NAME_SCORE;
    }

    private static boolean hasProductNameLabelEvidence(List<String> evidence) {
        if (evidence == null) {
            return false;
        }
        for (String item : evidence) {
            if (RecognitionTextCleaner.hasProductNameLabel(item)) {
                return true;
            }
        }
        return false;
    }

    private static int similarCandidateIndex(List<PackagingTextAnalyzer.Candidate> candidates, String text) {
        for (int index = 0; index < candidates.size(); index++) {
            if (RecognitionTextCleaner.productNamesSimilar(candidates.get(index).text, text)) {
                return index;
            }
        }
        return -1;
    }

    private static final class Frame {
        final String barcode;
        final List<PackagingTextAnalyzer.Candidate> packagingCandidates;
        final DateOcrParser.Result ocrResult;
        final String rawText;

        Frame(
                String barcode,
                List<PackagingTextAnalyzer.Candidate> packagingCandidates,
                DateOcrParser.Result ocrResult,
                String rawText
        ) {
            this.barcode = barcode == null ? "" : barcode;
            this.packagingCandidates = packagingCandidates == null
                    ? Collections.<PackagingTextAnalyzer.Candidate>emptyList()
                    : packagingCandidates;
            this.ocrResult = ocrResult;
            this.rawText = rawText == null ? "" : rawText;
        }
    }

    private static final class PackagingAggregate {
        String text;
        double representativeScore;
        double weightedScore;
        double totalWeight;
        int votes;
        final List<String> evidence = new ArrayList<String>();

        PackagingAggregate(String text) {
            this.text = text;
        }

        void add(PackagingTextAnalyzer.Candidate candidate, double recencyWeight) {
            votes++;
            weightedScore += candidate.score * recencyWeight;
            totalWeight += recencyWeight;
            boolean currentCanonical = RecognitionTextCleaner.isCanonicalFoodName(text);
            boolean incomingCanonical = RecognitionTextCleaner.isCanonicalFoodName(candidate.text);
            if ((incomingCanonical && !currentCanonical)
                    || (incomingCanonical == currentCanonical
                    && (candidate.score > representativeScore
                    || (candidate.score == representativeScore && candidate.text.length() > text.length())))) {
                text = candidate.text;
                representativeScore = candidate.score;
            }
            addEvidence(candidate.text);
            for (String item : candidate.evidence) {
                addEvidence(item);
            }
        }

        CandidateScore candidateScore() {
            double averageScore = totalWeight <= 0d ? representativeScore : weightedScore / totalWeight;
            return new CandidateScore(averageScore + (Math.min(votes, 6) * 8d), votes);
        }

        PackagingTextAnalyzer.Candidate toCandidate() {
            CandidateScore result = candidateScore();
            return new PackagingTextAnalyzer.Candidate(text, result.score, result.votes, evidence);
        }

        private void addEvidence(String value) {
            String cleaned = FoodItem.cleanText(value);
            if (cleaned.length() > 0 && !evidence.contains(cleaned)) {
                evidence.add(cleaned);
            }
        }
    }

    private static final class CandidateScore {
        final double score;
        final int votes;

        CandidateScore(double score, int votes) {
            this.score = score;
            this.votes = votes;
        }
    }

    static final class Snapshot {
        final int frameCount;
        final String stableBarcode;
        final int barcodeVotes;
        final String stablePackagingName;
        final int packagingNameVotes;
        final List<PackagingTextAnalyzer.Candidate> rankedPackagingCandidates;
        final DateOcrFrameVoter.VoteResult stableDateVote;
        final DateOcrFrameVoter.VoteResult latestDateVote;
        final String latestRawText;

        Snapshot(
                int frameCount,
                String stableBarcode,
                int barcodeVotes,
                String stablePackagingName,
                int packagingNameVotes,
                DateOcrFrameVoter.VoteResult stableDateVote,
                DateOcrFrameVoter.VoteResult latestDateVote,
                String latestRawText
        ) {
            this(
                    frameCount,
                    stableBarcode,
                    barcodeVotes,
                    stablePackagingName,
                    packagingNameVotes,
                    Collections.<PackagingTextAnalyzer.Candidate>emptyList(),
                    stableDateVote,
                    latestDateVote,
                    latestRawText
            );
        }

        Snapshot(
                int frameCount,
                String stableBarcode,
                int barcodeVotes,
                String stablePackagingName,
                int packagingNameVotes,
                List<PackagingTextAnalyzer.Candidate> rankedPackagingCandidates,
                DateOcrFrameVoter.VoteResult stableDateVote,
                DateOcrFrameVoter.VoteResult latestDateVote,
                String latestRawText
        ) {
            this.frameCount = frameCount;
            this.stableBarcode = stableBarcode == null ? "" : stableBarcode;
            this.barcodeVotes = barcodeVotes;
            this.stablePackagingName = FoodItem.cleanText(stablePackagingName);
            this.packagingNameVotes = Math.max(0, packagingNameVotes);
            List<PackagingTextAnalyzer.Candidate> safeCandidates = rankedPackagingCandidates == null
                    ? Collections.<PackagingTextAnalyzer.Candidate>emptyList()
                    : new ArrayList<PackagingTextAnalyzer.Candidate>(rankedPackagingCandidates);
            this.rankedPackagingCandidates = Collections.unmodifiableList(safeCandidates);
            this.stableDateVote = stableDateVote;
            this.latestDateVote = latestDateVote;
            this.latestRawText = latestRawText == null ? "" : latestRawText;
        }

        boolean hasStableBarcode() {
            return BarcodeUtils.isSupportedProductCode(stableBarcode);
        }

        boolean hasStableDateCandidate() {
            return stableDateVote != null && stableDateVote.hasStableCandidate();
        }

        boolean hasStablePackagingName() {
            return stablePackagingName.length() > 0;
        }

        String bestPackagingNameForConfirmation() {
            if (hasStablePackagingName()) {
                return stablePackagingName;
            }
            return rankedPackagingCandidates.isEmpty()
                    ? ""
                    : rankedPackagingCandidates.get(0).text;
        }

        boolean hasFillableCandidate() {
            if (hasStableBarcode()) {
                return true;
            }
            if (bestPackagingNameForConfirmation().length() > 0) {
                return true;
            }
            if (!hasStableDateCandidate()) {
                return false;
            }
            return DateOcrResultPayload.hasUsableDraft(DateOcrResultPayload.toDraft(stableDateVote));
        }

        boolean hasAnySeenCandidate() {
            return hasFillableCandidate()
                    || !rankedPackagingCandidates.isEmpty()
                    || (latestDateVote != null
                    && (latestDateVote.hasStableCandidate() || latestDateVote.framesWithCandidates > 0));
        }

        List<String> statusHints() {
            List<String> hints = new ArrayList<String>();
            if (hasStableBarcode()) {
                hints.add("条码已锁定");
            }
            if (hasStableDateCandidate()) {
                hints.add("日期候选已稳定");
            } else if (latestDateVote != null && latestDateVote.hasConflict) {
                hints.add("日期候选冲突");
            }
            if (latestRawText.length() > 0) {
                hints.add("已看到包装文字");
            }
            return Collections.unmodifiableList(hints);
        }
    }
}
