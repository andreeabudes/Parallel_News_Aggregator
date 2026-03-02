import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Article {
	public String uuid;
	public String title;
	public String author;
	public String url;
	public String text;
	public String published;
	public String language;
	public List<String> categories;
}
