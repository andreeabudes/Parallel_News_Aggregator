import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

public class Tema1 {
	public static CyclicBarrier barrier;
	public static List<Article> allRawArticles = Collections.synchronizedList(new ArrayList<>());
	public static ObjectMapper mapper = new ObjectMapper();
	public static AtomicInteger countDuplicates = new AtomicInteger(0);
	public static ArrayList<String> absolutePaths = new ArrayList<>();
	static ArrayList<String> categories = new ArrayList<>();
	static ArrayList<String> languages = new ArrayList<>();
	static ArrayList<String> english_linking_words = new ArrayList<>();
	static ArrayList<String> articlePath = new ArrayList<>();
	public static List<Article> uniqueArticles = new ArrayList<>();
	public static ConcurrentHashMap<String, Set<String>> mapCategories = new ConcurrentHashMap<>();
	public static ConcurrentHashMap<String, Set<String>> mapLanguages = new ConcurrentHashMap<>();
	public static ConcurrentHashMap<String, Set<String>> keywordMap = new ConcurrentHashMap<>();
	public static List<Article> sortedArticles = new ArrayList<>();
	public static List<Pair> keywordResult = new ArrayList<>();

	// ajutator pt sortare
	static class Pair {
		String word;
		int count;
		Pair(String w, int c) { word = w; count = c; }
	}

	public static void main(String[] args) {
		int nrThreads = Integer.parseInt(args[0]);
		String articles = args[1];
		String aux = args[2];

		File articlesFile = new File(articles);
		String baseDir = articlesFile.getParent();

		Task[] threads = new Task[nrThreads];
		barrier = new CyclicBarrier(nrThreads);


		// citire paths
		try (BufferedReader br = new BufferedReader(new FileReader(articles))) {
			br.readLine();
			for (String line; (line = br.readLine()) != null;) {
				articlePath.add(line);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		// citire cai auxiliare
		try (BufferedReader br = new BufferedReader(new FileReader(aux))) {
			br.readLine();
			for (String line; (line = br.readLine()) != null;) {
				File file = new File(new File(aux).getParentFile(), line);
				String absolute = file.getCanonicalPath();
				absolutePaths.add(absolute);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		// languages
		try (BufferedReader b = new BufferedReader(new FileReader(absolutePaths.get(0)))) {
			b.readLine();
			for (String line; (line = b.readLine()) != null; ) {
				languages.add(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		// categories
		try (BufferedReader b = new BufferedReader(new FileReader(absolutePaths.get(1)))) {
			b.readLine();
			for (String line; (line = b.readLine()) != null; ) {
				categories.add(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		// english_linking_words
		try (BufferedReader b = new BufferedReader(new FileReader(absolutePaths.get(2)))) {
			b.readLine();
			for (String line; (line = b.readLine()) != null; ) {
				english_linking_words.add(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		for (int i = 0; i < nrThreads; i++) {
			threads[i] = new Task(i, nrThreads, barrier, articlePath, mapper, countDuplicates, baseDir);
			threads[i].start();
		}

		for (int i = 0; i < nrThreads; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
