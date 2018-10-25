package yoshikihigo.tinypdg.scorpio;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.core.dom.CompilationUnit;

import yoshikihigo.tinypdg.ast.TinyPDGASTVisitor;
import yoshikihigo.tinypdg.cfg.node.CFGNodeFactory;
import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.edge.PDGControlDependenceEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGDataDependenceEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGExecutionDependenceEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.pdg.node.PDGNodeFactory;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;
import yoshikihigo.tinypdg.scorpio.pdg.PDGMergedNode;


public class ScorpioTest {

	private static final int NUMBER_OF_THREADS = 1;
	private static final int SIZE_THRESHOLD = 1; //note: don't setup too large

	public static void main(String[] args) {
		try {

			final File target = new File("./src/test/java/test001/Test001.java");
			if (!target.exists()) {
				System.err.println("specified directory or file does not exist.");
				System.exit(0);
			}

			System.out.println("generating PDGs ... ");
	
			final PDG[] pdgArray;
			{
				final List<File> files = getFiles(target);
				final List<MethodInfo> methods = new ArrayList<MethodInfo>();
				for (final File file : files) {
					final CompilationUnit unit = TinyPDGASTVisitor.createAST(file);
					final TinyPDGASTVisitor visitor = new TinyPDGASTVisitor(file.getAbsolutePath(), unit, methods);
					unit.accept(visitor);
				}

				final SortedSet<PDG> pdgs = Collections.synchronizedSortedSet(new TreeSet<PDG>());
				final CFGNodeFactory cfgNodeFactory = new CFGNodeFactory();
				final PDGNodeFactory pdgNodeFactory = new PDGNodeFactory();
				final Thread[] pdgGenerationThreads = new Thread[NUMBER_OF_THREADS];
				for (int i = 0; i < pdgGenerationThreads.length; i++) {
					pdgGenerationThreads[i] = new Thread(
							new PDGGenerationThread(methods, pdgs,cfgNodeFactory, pdgNodeFactory,true, true, false,false,SIZE_THRESHOLD));
					pdgGenerationThreads[i].start();
				}
				for (final Thread thread : pdgGenerationThreads) {
					try {
						thread.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				pdgArray = pdgs.toArray(new PDG[0]);
			}
			SortedSet<PDGNode<?>> backwardslicenodes = new TreeSet<PDGNode<?>>();
			SortedSet<PDGNode<?>> forwardslicenodes = new TreeSet<PDGNode<?>>();
			PDGNode<?> enternode;
			for (final PDG pdg : pdgArray) {
				final SortedSet<PDGNode<?>> nodes = pdg.getAllNodes();
				enternode = pdg.getNodeofLine(nodes, 9);
				System.out.println(enternode.getText());
				pdg.getBackwardNodes(enternode, backwardslicenodes);
				pdg.getForwardNodes(enternode, forwardslicenodes);
				break;
			}
			for(final PDGNode<?> slicenode : backwardslicenodes){
				final String fromNodeNormalizedText = generateNodeText(slicenode);
				System.out.println(fromNodeNormalizedText);
			}
			for(final PDGNode<?> slicenode : forwardslicenodes){
				final String fromNodeNormalizedText = generateNodeText(slicenode);
				System.out.println(fromNodeNormalizedText);
			}

		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.exit(0);
		}
	}

	private static List<File> getFiles(final File file) {

		final List<File> files = new ArrayList<File>();

		if (file.isFile()) {
			if (file.getName().endsWith(".java")) {
				files.add(file);
			}
		}

		else if (file.isDirectory()) {
			for (final File child : file.listFiles()) {
				files.addAll(getFiles(child));
			}
		}

		else {
			assert false : "\"file\" is invalid.";
		}

		return files;
	}
	
	private static String generateNodeText(final PDGNode<?> node) {

		final StringBuilder text = new StringBuilder();
		if (node instanceof PDGMergedNode) {
			for (final PDGNode<?> originalNode : ((PDGMergedNode) node)
					.getOriginalNodes()) {
				text.append(generateProgramElementText(originalNode.core));
				text.append(":");
			}
			text.deleteCharAt(text.length() - 1);
		} else {
			text.append(generateProgramElementText(node.core));
		}
		return text.toString();
	}

	private static String generateProgramElementText(final ProgramElementInfo element) {

		final StringBuilder text = new StringBuilder();
		if (element.startLine == element.endLine) {
			text.append(Integer.toString(element.startLine));
		} else {
			text.append(Integer.toString(element.startLine));
			text.append("-");
			text.append(Integer.toString(element.endLine));
		}
		return text.toString();
	}
	

}