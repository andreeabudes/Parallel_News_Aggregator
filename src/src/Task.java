import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Task extends Thread {
	private final String baseDirectory;
	private final int id;
	private final CyclicBarrier barrier;
	private final List<String> files;
	private final int nrThreads;
	private final ObjectMapper mapper;
	private final AtomicInteger countDuplicates;

	public Task(int id, int nrThreads, CyclicBarrier barrier, List<String> files,
				ObjectMapper mapper, AtomicInteger countDuplicates, String baseDirectory) {
		this.id = id;
		this.nrThreads = nrThreads;
		this.barrier = barrier;
		this.files = files;
		this.mapper = mapper;
		this.countDuplicates = countDuplicates;
		this.baseDirectory = baseDirectory;
	}

	@Override
	public void run() {

		// citire articole JSON
		int N = files.size();
		int start = (int) Math.floor(id * (double) N / nrThreads);
		int end = Math.min(N, (int) Math.floor((id + 1) * (double) N / nrThreads));

		for (int i = start; i < end; i++) {
			String path = files.get(i).trim();

			File f;
			try {
				f = new File(baseDirectory, path).getCanonicalFile();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			if (!f.exists()) {
				continue;
			}

			try {
				Article[] arr = mapper.readValue(f, Article[].class);
				Collections.addAll(Tema1.allRawArticles, arr);
			} catch (IOException e) {
				System.err.println("thread " + id + " - eroare JSON: " + path);
			}
		}

		awaitBarrier();

		if (id == 0) {
			// eliminare duplicate
			Set<String> duplicatedUUID = new HashSet<>();
			Set<String> seenUUID = new HashSet<>();

			Set<String> duplicatedTitle = new HashSet<>();
			Set<String> seenTitle = new HashSet<>();

			for (Article art : Tema1.allRawArticles) {
				if (art.uuid != null && !art.uuid.isEmpty()) {
					if (seenUUID.contains(art.uuid)) {
						duplicatedUUID.add(art.uuid);
					} else {
						seenUUID.add(art.uuid);
					}
				}
				if (art.title != null && !art.title.isEmpty()) {
					if (seenTitle.contains(art.title)) {
						duplicatedTitle.add(art.title);
					} else {
						seenTitle.add(art.title);
					}
				}
			}

			// golesc lista ca sa pot pune articole noi pt testul urmator
			Tema1.uniqueArticles.clear();
			// resetez contorul pt duplicate de fiecare data
			countDuplicates.set(0);

			// verific a 2-a oara duplicatele
			for (Article art : Tema1.allRawArticles) {
				boolean dupUUID = art.uuid != null && !art.uuid.isEmpty() && duplicatedUUID.contains(art.uuid);
				boolean dupTitle = art.title != null && !art.title.isEmpty() && duplicatedTitle.contains(art.title);

				if (dupUUID || dupTitle) {
					countDuplicates.incrementAndGet();
				} else {
					Tema1.uniqueArticles.add(art);
				}
			}

			// convertesc textul din lista de categorii
			Tema1.categories.replaceAll(s -> s.trim().replace(",", "").replace(" ", "_"));

			// initializez mapurile
			Tema1.mapCategories.clear();
			for (String cat : Tema1.categories) {
				Tema1.mapCategories.put(cat, ConcurrentHashMap.newKeySet());
			}

			Tema1.mapLanguages.clear();
			for (String lang : Tema1.languages) {
				Tema1.mapLanguages.put(lang, ConcurrentHashMap.newKeySet());
			}

			Tema1.keywordMap.clear();
		}

		awaitBarrier();

		// impart articolele intre threaduri
		// M = nr de articole unice
		int M = Tema1.uniqueArticles.size();
		int begin = (int) Math.floor(id * (double) M / nrThreads);
		int finale = Math.min(M, (int) Math.floor((id + 1) * (double) M / nrThreads));

		// imi copiez intr-un set cuvintele de legatura pt fiecare thread
		Set<String> linkWords = new HashSet<>(Tema1.english_linking_words);

		// incep parsarea articolelor
		for (int i = begin; i < finale; i++) {
			Article art = Tema1.uniqueArticles.get(i);
			if (art == null) continue;

			String uuid = art.uuid;

			// CATEGORII
			if (art.categories != null && uuid != null) {
				for (String c : art.categories) {
					if (c == null) continue;
					String converted = c.trim().replace(",", "").replace(" ", "_");
					// imi fac un set in care stochez articolele care fac parte dintr-o anumita categorie
					Set<String> setCat = Tema1.mapCategories.get(converted);
					if (setCat != null) {
						setCat.add(uuid);
					}
				}
			}

			// LIMBI
			if (art.language != null && uuid != null) {
				Set<String> setLang = Tema1.mapLanguages.get(art.language);
				if (setLang != null) {
					setLang.add(uuid);
				}
			}

			// KEYWORDS_EN
			if ("english".equals(art.language) && art.text != null && uuid != null) {
				// impart articolul in cuvinte
				String[] words = art.text.toLowerCase().split("\\s+");
				// tot asa imi fac un set pt cuvintele din articol
				Set<String> wordSeen = new HashSet<>();

				for (String w : words) {
					// prelucrez fiecare cuvant
					String newWord = w.replaceAll("[^a-z]", "");
					if (newWord.isEmpty()) continue;
					if (linkWords.contains(newWord)) continue;
					wordSeen.add(newWord);
				}

				// pt fiecare cuvant prelucrat adaug uuid-ul in map
				for (String w : wordSeen) {
					Tema1.keywordMap.computeIfAbsent(w, k -> ConcurrentHashMap.newKeySet()).add(uuid);
				}
			}
		}

		awaitBarrier();

		// scriere fisiere si rapoarte
		if (id == 0) {
			// categorii
			for (String cat : Tema1.categories) {
				Set<String> uuids = Tema1.mapCategories.get(cat);

				// ca sa nu mi se creeze fisiere goale si pt categorii care nu sunt in articol
				if (uuids == null || uuids.isEmpty())  continue;

				List<String> sorted = new ArrayList<>(uuids);
				sorted.sort(String::compareTo);

				try (PrintWriter pw = new PrintWriter(new FileWriter(cat + ".txt"))) {
					for (String u : sorted) {
						pw.println(u);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			// limbi
			for (String lang : Tema1.languages) {
				Set<String> uuids = Tema1.mapLanguages.get(lang);
				if (uuids == null || uuids.isEmpty()) continue;

				List<String> sorted = new ArrayList<>(uuids);
				sorted.sort(String::compareTo);

				try (PrintWriter pw = new PrintWriter(new FileWriter(lang + ".txt"))) {
					for (String id : sorted) {
						pw.println(id);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			// all_articles.txt
			Tema1.sortedArticles = new ArrayList<>(Tema1.uniqueArticles);
			// sortare
			Collections.sort(Tema1.sortedArticles, new Comparator<Article>() {
				@Override
				public int compare(Article a, Article b) {
					int cmp = b.published.compareTo(a.published);
					if (cmp != 0) {
						return cmp;
					}
					return a.uuid.compareTo(b.uuid);
				}
			});

			try (PrintWriter pw = new PrintWriter(new FileWriter("all_articles.txt"))) {
				for (Article art : Tema1.sortedArticles) {
					pw.println(art.uuid + " " + art.published);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			// keywords_count.txt
			Tema1.keywordResult.clear();
			for (var entry : Tema1.keywordMap.entrySet()) {
				Tema1.keywordResult.add(new Tema1.Pair(entry.getKey(), entry.getValue().size()));
			}

			Collections.sort(Tema1.keywordResult, new Comparator<Tema1.Pair>() {
				@Override
				public int compare(Tema1.Pair a, Tema1.Pair b) {
					// sortare descrescatoare
					if (b.count != a.count) {
						return b.count - a.count;
					}
					// sortare lexicografica
					return a.word.compareTo(b.word);
				}
			});

			try (PrintWriter pw = new PrintWriter(new FileWriter("keywords_count.txt"))) {
				for (Tema1.Pair p : Tema1.keywordResult) {
					pw.println(p.word + " " + p.count);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			// reports.txt
			try (PrintWriter pw = new PrintWriter(new FileWriter("reports.txt"))) {
				pw.println("duplicates_found - " + countDuplicates.get());
				pw.println("unique_articles - " + Tema1.uniqueArticles.size());

				// best_author
				Map<String, Integer> authorCount = new HashMap<>();
				for (Article art : Tema1.uniqueArticles) {
					if (art.author == null) continue;
					authorCount.put(art.author, authorCount.getOrDefault(art.author, 0) + 1);
				}

				String bestAuthor = null;
				int bestAuthorCount = 0;
				for (var e : authorCount.entrySet()) {
					String a = e.getKey();
					int c = e.getValue();
					if (c > bestAuthorCount || (c == bestAuthorCount && (bestAuthor == null || a.compareTo(bestAuthor) < 0))) {
						bestAuthor = a;
						bestAuthorCount = c;
					}
				}
				pw.println("best_author - " + bestAuthor + " " + bestAuthorCount);

				// top_language
				Map<String, Integer> langCount = new HashMap<>();
				for (Article art : Tema1.uniqueArticles) {
					if (art.language == null) continue;
					langCount.put(art.language, langCount.getOrDefault(art.language, 0) + 1);
				}

				String topLang = null;
				int topLangCount = 0;
				for (var e : langCount.entrySet()) {
					String l = e.getKey();
					int c = e.getValue();
					if (c > topLangCount || (c == topLangCount && (topLang == null || l.compareTo(topLang) < 0))) {
						topLang = l;
						topLangCount = c;
					}
				}
				pw.println("top_language - " + topLang + " " + topLangCount);

				// top_category
				String bestCat = null;
				int bestCatCount = 0;
				for (String c : Tema1.categories) {
					Set<String> s = Tema1.mapCategories.get(c);
					int cnt;
					if (s == null) {
						cnt = 0;
					} else {
						cnt = s.size();
					}
					if (cnt > bestCatCount || (cnt == bestCatCount && (bestCat == null || c.compareTo(bestCat) < 0))) {
						bestCat = c;
						bestCatCount = cnt;
					}
				}
				pw.println("top_category - " + bestCat + " " + bestCatCount);

				// most_recent_article
				Article mostRecent = Tema1.sortedArticles.get(0);
				pw.println("most_recent_article - " + mostRecent.published + " " + mostRecent.url);

				// top_keyword_en
				Tema1.Pair topKW = Tema1.keywordResult.get(0);
				pw.println("top_keyword_en - " + topKW.word + " " + topKW.count);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void awaitBarrier() {
		try {
			barrier.await();
		} catch (Exception e) {}
	}
}
