package sampler.unsupervised;

import core.AbstractSampler;
import core.AbstractSampler.InitialState;
import data.TextDataset;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Stack;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.Options;
import sampling.likelihood.DirMult;
import util.CLIUtils;
import util.IOUtils;
import util.MiscUtils;

/**
 * Implementation of a hierarchy of LDAs.
 *
 * @author vietan
 */
public class RecursiveLDA extends AbstractSampler {

    // hyperparameters
    private double[] alphas;
    private double[] betas;
    // inputs
    protected int[][] words;
    protected ArrayList<Integer> docIndices;
    private int[] Ks; // number of children per node at each level
    protected int V; // vocabulary size
    // configure
    protected double[][] priorTopics; // prior for 1st-level topics
    // derived
    private int L; // number of levels
    protected int D; // number of documents
    // latent
    protected int[][][] zs;
    // internal
    private int numTokens;
    private RLDA rootLDA;

    public RecursiveLDA() {
        this.basename = "RecursiveLDA";
    }

    public RecursiveLDA(String basename) {
        this.basename = basename;
    }

    public RLDA getRoot() {
        return this.rootLDA;
    }

    public int getNumLevels() {
        return this.L;
    }

    public void configure(String folder,
            int V, int[] Ks,
            double[] alphas,
            double[] betas,
            InitialState initState,
            boolean paramOpt,
            int burnin, int maxiter, int samplelag, int repInt) {
        if (verbose) {
            logln("Configuring ...");
        }
        this.folder = folder;
        this.Ks = Ks;
        this.V = V;
        this.L = this.Ks.length;

        this.alphas = alphas;
        this.betas = betas;

        this.hyperparams = new ArrayList<Double>();
        for (double alpha : alphas) {
            this.hyperparams.add(alpha);
        }
        for (double beta : betas) {
            this.hyperparams.add(beta);
        }
        this.sampledParams = new ArrayList<ArrayList<Double>>();
        this.sampledParams.add(cloneHyperparameters());

        this.BURN_IN = burnin;
        this.MAX_ITER = maxiter;
        this.LAG = samplelag;
        this.REP_INTERVAL = repInt;

        this.initState = initState;
        this.paramOptimized = paramOpt;
        this.prefix += initState.toString();
        this.setName();

        if (alphas.length != L) {
            throw new RuntimeException("Dimensions mismatch. "
                    + alphas.length + " vs. " + L);
        }
        if (betas.length != L) {
            throw new RuntimeException("Dimensions mismatch. "
                    + betas.length + " vs. " + L);
        }

        if (verbose) {
            logln("--- folder\t" + folder);
            logln("--- # topics:\t" + MiscUtils.arrayToString(Ks));
            logln("--- vocab size:\t" + V);
            logln("--- alphas:\t" + MiscUtils.arrayToString(alphas));
            logln("--- betas:\t" + MiscUtils.arrayToString(betas));
            logln("--- burn-in:\t" + BURN_IN);
            logln("--- max iter:\t" + MAX_ITER);
            logln("--- sample lag:\t" + LAG);
            logln("--- paramopt:\t" + paramOptimized);
            logln("--- initialize:\t" + initState);
        }
    }

    protected void setName() {
        this.name = this.prefix
                + "_" + this.basename
                + "_K";
        for (int K : Ks) {
            this.name += "-" + K;
        }
        this.name += "_B-" + BURN_IN
                + "_M-" + MAX_ITER
                + "_L-" + LAG
                + "_a";
        for (double alpha : alphas) {
            this.name += "-" + alpha;
        }
        this.name += "_b";
        for (double beta : betas) {
            this.name += "-" + beta;
        }
        this.name += "_opt-" + this.paramOptimized;
    }

    /**
     * Set training data.
     *
     * @param docWords All documents
     * @param docIndices Indices of selected documents. If this is null, all
     * documents are considered.
     */
    public void train(int[][] docWords, ArrayList<Integer> docIndices) {
        this.docIndices = docIndices;
        if (this.docIndices == null) { // add all documents
            this.docIndices = new ArrayList<>();
            for (int dd = 0; dd < docWords.length; dd++) {
                this.docIndices.add(dd);
            }
        }
        this.numTokens = 0;
        this.D = this.docIndices.size();
        this.words = new int[D][];
        for (int ii = 0; ii < D; ii++) {
            int dd = this.docIndices.get(ii);
            this.words[ii] = docWords[dd];
            this.numTokens += this.words[ii].length;
        }

        if (verbose) {
            logln("--- # all docs:\t" + words.length);
            logln("--- # selected docs:\t" + D);
            logln("--- # tokens:\t" + numTokens);
        }
    }

    public void setPriorTopics(double[][] priorTopics) {
        this.priorTopics = priorTopics;
    }

    public RLDA[] getAssignedPath(int d, int n) {
        RLDA[] path = new RLDA[L - 1];
        RLDA parent = rootLDA;
        for (int l = 0; l < L - 1; l++) {
            path[l] = parent.getChildren().get(zs[l][d][n]);
            parent = path[l];
        }
        return path;
    }

    public RLDA getAssignedLeaf(int d, int n) {
        RLDA[] path = getAssignedPath(d, n);
        return path[path.length - 1];
    }

    public int[][][] getAssingments() {
        return this.zs;
    }

    @Override
    public void initialize() {
        if (verbose) {
            logln("Initializing ...");
        }

        zs = new int[L][][];
        for (int l = 0; l < L; l++) {
            zs[l] = new int[D][];
            for (int d = 0; d < D; d++) {
                zs[l][d] = new int[words[d].length];
            }
        }

        boolean[][] valid = new boolean[D][];
        for (int d = 0; d < D; d++) {
            valid[d] = new boolean[words[d].length];
            Arrays.fill(valid[d], true);
        }
        rootLDA = new RLDA(0, 0, valid, null);

        if (debug) {
            validate("Initialized");
        }
    }

    @Override
    public void iterate() {
        if (verbose) {
            logln("Iterating ...");
        }
        recursive(0, 0, rootLDA, null);
    }

    public void iterate(int[][] seededZs) {
        recursive(0, 0, rootLDA, seededZs);
    }

    private void recursive(int index, int level, RLDA rlda, int[][] seededZs) {
        if (verbose) {
            System.out.println();
            logln("Sampling LDA " + rlda.getPathString());
        }

        rlda.setVerbose(verbose);
        rlda.setDebug(debug);
        rlda.setLog(false);
        rlda.setReport(false);
        rlda.configure(null, V, Ks[level],
                alphas[level], betas[level],
                initState,
                paramOptimized,
                BURN_IN, MAX_ITER, LAG, REP_INTERVAL);
        rlda.train(words, null);
        if (level == 0) {
            rlda.initialize(null, this.priorTopics);
        } else {
            rlda.initialize();
        }

        if (seededZs != null && level == 0) { // use seeded assignments for the 1st level LDA
            if (verbose) {
                logln("--- Using seeded assgnments ...");
            }
            for (int d = 0; d < D; d++) {
                System.arraycopy(seededZs[d], 0, zs[level][d], 0, words[d].length);
            }
        } else {
            rlda.iterate();
            for (int d = 0; d < D; d++) {
                for (int n = 0; n < words[d].length; n++) {
                    if (rlda.getValid()[d][n]) {
                        zs[level][d][n] = rlda.z[d][n];
                    }
                }
            }
        }

        if (level++ == L - 1) {
            return;
        }

        for (int k = 0; k < Ks[level - 1]; k++) {
            boolean[][] subValid = new boolean[D][];
            for (int d = 0; d < D; d++) {
                subValid[d] = new boolean[words[d].length];
                Arrays.fill(subValid[d], false);
                for (int n = 0; n < words[d].length; n++) {
                    if (!rlda.getValid()[d][n]) {
                        continue;
                    }
                    if (rlda.z[d][n] == k) {
                        subValid[d][n] = true;
                    }
                }
            }
            RLDA subRLda = new RLDA(k, level, subValid, rlda);
            rlda.addChild(subRLda);
            recursive(index, level, subRLda, seededZs);
        }
    }

    @Override
    public String getCurrentState() {
        StringBuilder str = new StringBuilder();
        return str.toString();
    }

    @Override
    public double getLogLikelihood() {
        return 0;
    }

    @Override
    public double getLogLikelihood(ArrayList<Double> newParams) {
        return 0;
    }

    @Override
    public void updateHyperparameters(ArrayList<Double> newParams) {
    }

    @Override
    public void validate(String msg) {
    }

    @Override
    public void outputState(String filepath) {
        if (verbose) {
            logln("--- Outputing current state to " + filepath);
        }

        try {
            StringBuilder modelStr = new StringBuilder();
            Stack<RLDA> stack = new Stack<RLDA>();
            stack.add(rootLDA);
            while (!stack.isEmpty()) {
                RLDA node = stack.pop();
                for (RLDA child : node.getChildren()) {
                    stack.add(child);
                }
                modelStr.append(node.getPathString()).append("\n");
                for (int k = 0; k < Ks[node.getLevel()]; k++) {
                    modelStr.append(DirMult.output(node.topicWords[k])).append("\n");
                }
            }

            StringBuilder assignStr = new StringBuilder();
            for (int l = 0; l < L; l++) {
                assignStr.append(l).append("\n");
                for (int d = 0; d < D; d++) {
                    assignStr.append(d).append("\n");
                    for (int n = 0; n < words[d].length; n++) {
                        assignStr.append(zs[l][d][n]).append("\t");
                    }
                    assignStr.append("\n");
                }
            }

            // output to a compressed file
            this.outputZipFile(filepath, modelStr.toString(), assignStr.toString());
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public void inputState(String filepath) {
        if (verbose) {
            logln("--- Reading state from " + filepath);
        }

        inputAssignments(filepath);

        boolean[][] valid = new boolean[D][];
        for (int d = 0; d < D; d++) {
            valid[d] = new boolean[words[d].length];
            Arrays.fill(valid[d], true);
        }
        this.rootLDA = new RLDA(0, 0, valid, null);
        Stack<RLDA> stack = new Stack<RLDA>();
        stack.add(rootLDA);

        while (!stack.isEmpty()) {
            RLDA node = stack.pop();
            node.setVerbose(false);
            int level = node.getLevel();
            node.configure(null, V, Ks[level],
                    alphas[level], betas[level],
                    initState,
                    paramOptimized,
                    BURN_IN, MAX_ITER, LAG, REP_INTERVAL);
            node.train(words, null);
            node.initializeModelStructure(null);
            node.initializeDataStructure(null);

            if (level == L - 1) {
                continue;
            }
            for (int k = 0; k < Ks[level]; k++) {
                RLDA child = new RLDA(k, level + 1, null, node);
                node.addChild(child);
                stack.add(child);
            }
        }

        for (int d = 0; d < D; d++) {
            for (int n = 0; n < words[d].length; n++) {
                int[] path = new int[L];
                for (int l = 0; l < L; l++) {
                    path[l] = zs[l][d][n];
                }
                assign(rootLDA, path, words[d][n]);
            }
        }

        if (debug) {
            validate("Input from " + filepath);
        }
    }

    private void assign(RLDA node, int[] path, int obs) {
        int level = node.getLevel();
        node.topicWords[path[level]].increment(obs);

        if (level < L - 1) {
            assign(node.getChildren().get(path[level]), path, obs);
        }
    }

    void inputAssignments(String zipFilepath) {
        if (verbose) {
            logln("--- --- Loading assignments from " + zipFilepath + "\n");
        }

        zs = new int[L][][];
        for (int l = 0; l < L; l++) {
            zs[l] = new int[D][];
            for (int d = 0; d < D; d++) {
                zs[l][d] = new int[words[d].length];
            }
        }

        try {
            String filename = IOUtils.removeExtension(IOUtils.getFilename(zipFilepath));
            BufferedReader reader = IOUtils.getBufferedReader(zipFilepath, filename + AssignmentFileExt);
            String line;
            String[] sline;
            while ((line = reader.readLine()) != null) {
                int l = Integer.parseInt(line);
                for (int d = 0; d < D; d++) {
                    int docIdx = Integer.parseInt(reader.readLine());
                    if (docIdx != d) {
                        throw new RuntimeException("Mismatch. " + d + " vs. " + docIdx);
                    }

                    // if this document is empty
                    line = reader.readLine().trim();
                    if (line.isEmpty()) {
                        zs[l][d] = new int[0];
                    } else {
                        sline = line.split("\t");
                        if (sline.length != words[d].length) {
                            throw new RuntimeException("Mismatch. "
                                    + sline.length
                                    + " vs. " + words[d].length
                                    + ". in document " + d);
                        }
                        for (int n = 0; n < words[d].length; n++) {
                            zs[l][d][n] = Integer.parseInt(sline[n]);
                        }
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Exception while loading assignments from "
                    + zipFilepath);
        }
    }

    void inputModel(String zipFilepath) {
        if (verbose) {
            logln("--- --- Loading model from " + zipFilepath + "\n");
        }

        try {
            // initialize
            rootLDA = new RLDA(0, 0, null, null);

            String filename = IOUtils.removeExtension(IOUtils.getFilename(zipFilepath));
            BufferedReader reader = IOUtils.getBufferedReader(zipFilepath, filename + ModelFileExt);

            String line;
            HashMap<String, RLDA> nodeMap = new HashMap<String, RLDA>();
            while ((line = reader.readLine()) != null) {
                String pathStr = line;

                // create node
                int lastColonIndex = pathStr.lastIndexOf(":");
                RLDA parent = null;
                if (lastColonIndex != -1) {
                    parent = nodeMap.get(pathStr.substring(0, lastColonIndex));
                }

                String[] pathIndices = pathStr.split(":");
                int nodeIndex = Integer.parseInt(pathIndices[pathIndices.length - 1]);
                int nodeLevel = pathIndices.length - 1;
                RLDA node = new RLDA(nodeIndex, nodeLevel, null, parent);

                DirMult[] topics = new DirMult[Ks[nodeLevel]];
                for (int k = 0; k < Ks[nodeLevel]; k++) {
                    topics[k] = DirMult.input(reader.readLine());
                }
                node.topicWords = topics;

                if (node.getLevel() == 0) {
                    rootLDA = node;
                }

                if (parent != null) {
                    parent.getChildren().add(node.getIndex(), node);
                }

                nodeMap.put(pathStr, node);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Exception while loading model from "
                    + zipFilepath);
        }
    }

    public void outputTopicTopWords(File file, int numTopWords) throws Exception {
        if (this.wordVocab == null) {
            throw new RuntimeException("The word vocab has not been assigned yet");
        }

        if (verbose) {
            System.out.println("Outputing topics to file " + file);
        }

        BufferedWriter writer = IOUtils.getBufferedWriter(file);
        Stack<RLDA> stack = new Stack<RLDA>();
        stack.add(rootLDA);

        while (!stack.isEmpty()) {
            RLDA node = stack.pop();
            for (RLDA child : node.getChildren()) {
                stack.add(child);
            }

            int level = node.getLevel();
            if (node.getParent() != null) {
                double[] parentTopics = node.getParent().getTopicWords()[node.getIndex()].getDistribution();
                String[] parentTopWords = getTopWords(parentTopics, numTopWords);
                for (int l = 0; l < level; l++) {
                    writer.write("\t");
                }
                writer.write("[" + node.getPathString()
                        + ": " + node.getParent().getTopicWords()[node.getIndex()].getCountSum() + "]");
                for (String tw : parentTopWords) {
                    writer.write(" " + tw);
                }
                writer.write("\n");
            }

            if (node.getChildren().isEmpty()) {
                DirMult[] topics = node.getTopicWords();
                for (int k = 0; k < topics.length; k++) {
                    String[] topWords = getTopWords(topics[k].getDistribution(), numTopWords);
                    for (int l = 0; l < level + 1; l++) {
                        writer.write("\t");
                    }
                    writer.write("[" + node.getPathString() + ":" + k
                            + ":" + topics[k].getCountSum() + "]");
                    for (String tw : topWords) {
                        writer.write(" " + tw);
                    }
                    writer.write("\n");
                }
            }
        }
        writer.close();
    }

    public static String getHelpString() {
        return "java -cp dist/segan.jar " + RecursiveLDA.class.getName() + " -help";
    }

    public static String getExampleCmd() {
        return "java -cp \"dist/segan.jar:lib/*\" sampler.unsupervised.RecursiveLDA "
                + "--dataset amazon-data "
                + "--word-voc-file demo/amazon-data/format-unsupervised/amazon-data.wvoc "
                + "--word-file demo/amazon-data/format-unsupervised/amazon-data.dat "
                + "--info-file demo/amazon-data/format-unsupervised/amazon-data.docinfo "
                + "--output-folder demo/amazon-data/model-unsupervised "
                + "--burnIn 100 "
                + "--maxIter 250 "
                + "--sampleLag 30 "
                + "--report 5 "
                + "--report 1 "
                + "--Ks 10,5 "
                + "--alphas 0.1,0.1 "
                + "--betas 0.1,0.1 "
                + "-v -d";
    }

    private static void addOpitions() throws Exception {
        parser = new BasicParser();
        options = new Options();

        // data input
        addOption("dataset", "Dataset");
        addOption("word-voc-file", "Word vocabulary file");
        addOption("word-file", "Document word file");
        addOption("info-file", "Document info file");
        addOption("selected-docs-file", "(Optional) Indices of selected documents");

        // data output
        addOption("output-folder", "Output folder");

        // sampling
        addSamplingOptions();

        // parameters
        addOption("alphas", "Alphas");
        addOption("betas", "Betas");
        addOption("Ks", "Number of topics");
        addOption("num-top-words", "Number of top words per topic");

        // configurations
        addOption("init", "Initialization");

        options.addOption("v", false, "verbose");
        options.addOption("d", false, "debug");
        options.addOption("help", false, "Help");
        options.addOption("example", false, "Example command");
    }

    private static void runModel() throws Exception {
        // sampling configurations
        int numTopWords = CLIUtils.getIntegerArgument(cmd, "num-top-words", 20);
        int burnIn = CLIUtils.getIntegerArgument(cmd, "burnIn", 500);
        int maxIters = CLIUtils.getIntegerArgument(cmd, "maxIter", 1000);
        int sampleLag = CLIUtils.getIntegerArgument(cmd, "sampleLag", 50);
        int repInterval = CLIUtils.getIntegerArgument(cmd, "report", 25);
        boolean paramOpt = cmd.hasOption("paramOpt");
        String init = CLIUtils.getStringArgument(cmd, "init", "random");
        InitialState initState;
        switch (init) {
            case "random":
                initState = InitialState.RANDOM;
                break;
            case "preset":
                initState = InitialState.PRESET;
                break;
            default:
                throw new RuntimeException("Initialization " + init + " not supported");
        }

        double[] alphas = CLIUtils.getDoubleArrayArgument(cmd, "alphas", new double[]{0.1, 0.1}, ",");
        double[] betas = CLIUtils.getDoubleArrayArgument(cmd, "betas", new double[]{0.1, 0.1}, ",");
        int[] Ks = CLIUtils.getIntArrayArgument(cmd, "Ks", new int[]{10, 5}, ",");

        // data input
        String datasetName = cmd.getOptionValue("dataset");
        String wordVocFile = cmd.getOptionValue("word-voc-file");
        String docWordFile = cmd.getOptionValue("word-file");
        String docInfoFile = cmd.getOptionValue("info-file");

        // data output
        String outputFolder = cmd.getOptionValue("output-folder");

        TextDataset data = new TextDataset(datasetName);
        data.loadFormattedData(new File(wordVocFile),
                new File(docWordFile),
                new File(docInfoFile),
                null);
        int V = data.getWordVocab().size();

        RecursiveLDA sampler = new RecursiveLDA();
        sampler.setVerbose(cmd.hasOption("v"));
        sampler.setDebug(cmd.hasOption("d"));
        sampler.setLog(true);
        sampler.setReport(true);
        sampler.setWordVocab(data.getWordVocab());

        sampler.configure(outputFolder, V, Ks, alphas, betas,
                initState, paramOpt, burnIn, maxIters, sampleLag, repInterval);
        File samplerFolder = new File(sampler.getSamplerFolderPath());
        IOUtils.createFolder(samplerFolder);

        ArrayList<Integer> selectedDocIndices = null;
        if (cmd.hasOption("selected-docs-file")) {
            String selectedDocFile = cmd.getOptionValue("selected-docs-file");
            selectedDocIndices = new ArrayList<>();
            BufferedReader reader = IOUtils.getBufferedReader(selectedDocFile);
            String line;
            while ((line = reader.readLine()) != null) {
                int docIdx = Integer.parseInt(line);
                if (docIdx >= data.getDocIds().length) {
                    throw new RuntimeException("Out of bound. Doc index " + docIdx);
                }
                selectedDocIndices.add(Integer.parseInt(line));
            }
            reader.close();
        }

        sampler.train(data.getWords(), selectedDocIndices);
        sampler.initialize();
        sampler.iterate();
        sampler.outputTopicTopWords(new File(samplerFolder, TopWordFile), numTopWords);
    }

    public static void main(String[] args) {
        try {
            long sTime = System.currentTimeMillis();

            addOpitions();

            cmd = parser.parse(options, args);
            if (cmd.hasOption("help")) {
                CLIUtils.printHelp(getHelpString(), options);
                return;
            } else if (cmd.hasOption("example")) {
                System.out.println(getExampleCmd());
                return;
            }

            runModel();

            // date and time
            DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
            Date dateobj = new Date();
            long eTime = (System.currentTimeMillis() - sTime) / 1000;
            System.out.println("Elapsed time: " + eTime + "s");
            System.out.println("End time: " + df.format(dateobj));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }
}
