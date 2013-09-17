package util;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Class to store and perform stop word removal for English.
 *
 * @author vietan
 */
public class StopwordRemoval {

    private String[] stopwords;

    public StopwordRemoval() {
        this.stopwords = sws;
    }

    public StopwordRemoval(Stemmer stemmer) {
        this.stopwords = new String[sws.length];
        for (int ii = 0; ii < this.stopwords.length; ii++) {
            this.stopwords[ii] = stemmer.stem(sws[ii]);
        }
    }

    public StopwordRemoval(String stopwordFile) {
        try {
            ArrayList<String> stopwordList = new ArrayList<String>();
            BufferedReader reader = IOUtils.getBufferedReader(stopwordFile);
            String line;
            while ((line = reader.readLine()) != null) {
                stopwordList.add(line);
            }
            reader.close();
            this.stopwords = (String[]) stopwordList.toArray(new String[0]);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Throwing exception while loading stop words"
                    + " from file " + stopwordFile);
            System.err.println("Using the default list of stop words instead.");
            this.stopwords = sws;
        }
    }

    public void setStopwords(String[] sws) {
        this.stopwords = sws;
    }

    public String[] getStopwords() {
        return this.stopwords;
    }

    public boolean isStopword(String word) {
        return Arrays.binarySearch(this.stopwords, word) < 0 ? false : true;
    }
    private static String[] sws = {
        "a",
        "about",
        "above",
        "across",
        "after",
        "again",
        "against",
        "all",
        "almost",
        "alone",
        "along",
        "already",
        "also",
        "although",
        "always",
        "among",
        "an",
        "and",
        "another",
        "any",
        "anybody",
        "anyone",
        "anything",
        "anywhere",
        "are",
        "area",
        "areas",
        "around",
        "as",
        "ask",
        "asked",
        "asking",
        "asks",
        "at",
        "away",
        "b",
        "back",
        "backed",
        "backing",
        "backs",
        "be",
        "became",
        "because",
        "become",
        "becomes",
        "been",
        "before",
        "began",
        "behind",
        "being",
        "beings",
        "best",
        "better",
        "between",
        "big",
        "both",
        "but",
        "by",
        "c",
        "came",
        "can",
        "cannot",
        "case",
        "cases",
        "certain",
        "certainly",
        "clear",
        "clearly",
        "com",
        "come",
        "could",
        "d",
        "did",
        "differ",
        "different",
        "differently",
        "do",
        "does",
        "done",
        "down",
        "down",
        "downed",
        "downing",
        "downs",
        "during",
        "e",
        "each",
        "early",
        "either",
        "end",
        "ended",
        "ending",
        "ends",
        "enough",
        "even",
        "evenly",
        "ever",
        "every",
        "everybody",
        "everyone",
        "everything",
        "everywhere",
        "f",
        "face",
        "faces",
        "fact",
        "facts",
        "far",
        "felt",
        "few",
        "find",
        "finds",
        "first",
        "for",
        "four",
        "from",
        "full",
        "fully",
        "further",
        "furthered",
        "furthering",
        "furthers",
        "g",
        "gave",
        "general",
        "generally",
        "get",
        "gets",
        "give",
        "given",
        "gives",
        "go",
        "going",
        "good",
        "goods",
        "got",
        "great",
        "greater",
        "greatest",
        "group",
        "grouped",
        "grouping",
        "groups",
        "h",
        "had",
        "has",
        "have",
        "having",
        "he",
        "her",
        "here",
        "herself",
        "high",
        "higher",
        "highest",
        "him",
        "himself",
        "his",
        "how",
        "however",
        "http",
        "i",
        "if",
        "important",
        "in",
        "interest",
        "interested",
        "interesting",
        "interests",
        "into",
        "is",
        "it",
        "its",
        "itself",
        "j",
        "just",
        "k",
        "keep",
        "keeps",
        "kind",
        "knew",
        "know",
        "known",
        "knows",
        "l",
        "large",
        "largely",
        "last",
        "later",
        "latest",
        "least",
        "less",
        "let",
        "lets",
        "like",
        "likely",
        "long",
        "longer",
        "longest",
        "m",
        "made",
        "make",
        "making",
        "man",
        "many",
        "may",
        "me",
        "member",
        "members",
        "men",
        "might",
        "more",
        "most",
        "mostly",
        "mr",
        "mrs",
        "much",
        "must",
        "my",
        "myself",
        "n",
        "necessary",
        "need",
        "needed",
        "needing",
        "needs",
        "never",
        "new",
        "new",
        "newer",
        "newest",
        "next",
        "no",
        "nobody",
        "non",
        "noone",
        "not",
        "nothing",
        "now",
        "nowhere",
        "number",
        "numbers",
        "o",
        "of",
        "off",
        "often",
        "old",
        "older",
        "oldest",
        "on",
        "once",
        "one",
        "only",
        "open",
        "opened",
        "opening",
        "opens",
        "or",
        "order",
        "ordered",
        "ordering",
        "orders",
        "other",
        "others",
        "our",
        "out",
        "over",
        "p",
        "part",
        "parted",
        "parting",
        "parts",
        "per",
        "perhaps",
        "place",
        "places",
        "point",
        "pointed",
        "pointing",
        "points",
        "possible",
        "present",
        "presented",
        "presenting",
        "presents",
        "problem",
        "problems",
        "put",
        "puts",
        "q",
        "quite",
        "r",
        "rather",
        "really",
        "right",
        "right",
        "room",
        "rooms",
        "s",
        "said",
        "same",
        "saw",
        "say",
        "says",
        "second",
        "seconds",
        "see",
        "seem",
        "seemed",
        "seeming",
        "seems",
        "sees",
        "several",
        "shall",
        "she",
        "should",
        "show",
        "showed",
        "showing",
        "shows",
        "side",
        "sides",
        "since",
        "small",
        "smaller",
        "smallest",
        "so",
        "some",
        "somebody",
        "someone",
        "something",
        "somewhere",
        "state",
        "states",
        "still",
        "still",
        "such",
        "sure",
        "t",
        "take",
        "taken",
        "than",
        "that",
        "the",
        "their",
        "them",
        "then",
        "there",
        "therefore",
        "these",
        "they",
        "thing",
        "things",
        "think",
        "thinks",
        "this",
        "those",
        "though",
        "thought",
        "thoughts",
        "three",
        "through",
        "thus",
        "to",
        "today",
        "together",
        "too",
        "took",
        "toward",
        "turn",
        "turned",
        "turning",
        "turns",
        "two",
        "u",
        "under",
        "until",
        "up",
        "upon",
        "us",
        "use",
        "used",
        "uses",
        "v",
        "very",
        "w",
        "want",
        "wanted",
        "wanting",
        "wants",
        "was",
        "way",
        "ways",
        "we",
        "well",
        "wells",
        "went",
        "were",
        "what",
        "when",
        "where",
        "whether",
        "which",
        "while",
        "who",
        "whole",
        "whose",
        "why",
        "will",
        "with",
        "within",
        "without",
        "work",
        "worked",
        "working",
        "works",
        "would",
        "www",
        "x",
        "y",
        "year",
        "years",
        "yet",
        "you",
        "young",
        "younger",
        "youngest",
        "your",
        "yours",
        "z"
    };
}