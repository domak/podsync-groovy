import java.text.SimpleDateFormat
import com.sun.xml.internal.ws.message.saaj.SAAJHeader

// this script use google reader feed to retrieve rss item
// google reader cache rss items so you can find more items than in the "direct" rss feed

class PodcastSync {


  static final def MAX_DOWNLOAD_FILE = 5

  //---------------------------------------------------------------------------

  public static void main(String[] args) {
    def psync = new PodcastSync()
    psync.process()
  }


  def process() {
    println "podcast inter downloader (from google reader)"
    def rssUrls = ["http://radiofrance-podcast.net/podcast09/rss_10906.xml", "http://radiofrance-podcast.net/podcast09/rss_11335.xml"]
    def googleUser = ""
    def googlePassword = ""
    def targetDirectory = "./out"
    // def targetDirectory = "/media/COWON/Music/new/inter"

    def token = authenticate(googleUser, googlePassword)
    rssUrls.each {
      def podcasts = getPodcastsFromGoogleFeed(token, it)
      podcasts.each {
        println "feed - found podcast url:" + it
      }

      def podcastId = extractPodcastId(extractPodcastFileName(podcasts[0]))
      def lastPodcast = getLastPodcast(targetDirectory, podcastId)
      println "podcastId: ${podcastId} - dir lastPodcast: ${lastPodcast}"
      deleteOldPodcasts(targetDirectory, podcastId)

      if (lastPodcast) {
        downloadPodcasts(podcasts, targetDirectory, lastPodcast)
      } else {
        downloadPodcasts(podcasts, targetDirectory, MAX_DOWNLOAD_FILE)
      }
    }
  }

  /**
   * Downloads podcasts more recents than last podcat (or 5 last if no last podcast).
   */
  def downloadPodcasts(podcastUrls, targetDirectory, String lastPodcast) {
    podcastUrls.each {
      def fileName = extractPodcastFileName(it)
      def podcastComparator = new PodcastComparator()
      if (podcastComparator.compare(new File(fileName), lastPodcast) > 0) {
        println "download: ${it}"
        download(it, fileName, targetDirectory)
      }
    }
  }

  /**
   * Downloads podcasts more recents than last podcat (or 5 last if no last podcast).
   */
  def downloadPodcasts(podcastUrls, targetDirectory, int count) {
    podcastUrls[0..count - 1].each {
      def fileName = extractPodcastFileName(it)
      println "download: ${it}"
      download(it, fileName, targetDirectory)
    }
  }

  /**
   * Downloads url content.
   * @param urlAsString the url
   * @param targetFileName
   * @param targetDirectory
   */
  private def download(urlAsString, targetFileName, targetDirectory) {
    def targetFile = new FileOutputStream(targetDirectory + "/" + targetFileName)
    def out = new BufferedOutputStream(targetFile)
    out << new URL(urlAsString).openStream()
    out.close()
  }

/**
 */
  def deleteOldPodcasts(targetDirectory, podcastId) {
    def dir = new File(targetDirectory)

    // filter files that matches with the podcast id
    def files = []
    dir.eachFile {File file ->
      if (file.getName().startsWith(podcastId)) {
        println "delete: ${file}"
        file.delete()
      }
    }
  }

/**
 * @return
 */
  def getLastPodcast(targetDirectory, podcastId) {
    def dir = new File(targetDirectory)

    // filter files that matches with the podcast id
    def files = []
    dir.eachFile {File file ->
      if (file.getName().startsWith(podcastId)) {
        files << file
      }
    }

    return files.isEmpty() ? null : files.sort(new PodcastComparator()).last().getName()
  }

/**
 * Extracts podcast file name.
 */
  def extractPodcastFileName(url) {
    def splittedString = url.split("/")
    splittedString[splittedString.length - 1]
  }

/**
 * Extracts podcast id
 */
  def extractPodcastId(fileName) {
    fileName.split("-")[0]
  }

/**
 * Authenticates to google with a POST connection.
 * @return the auth token
 */
  def authenticate(user, password) {

    def url = "https://www.google.com/accounts/ClientLogin".toURL()
    def queryString = "Email=${URLEncoder.encode(user)}&Passwd=${URLEncoder.encode(password)}&service=reader"

    def connection = url.openConnection()
    connection.requestMethod = "POST"
    connection.doOutput = true

    def writer = new OutputStreamWriter(connection.outputStream)
    writer.write(queryString)
    writer.flush()
    writer.close()
    connection.connect()

    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
      println "authen error - code: ${connection.responseCode} - message: ${connection.responseMessage} - content: ${connection.content.text}"
      return null
    }

    // extract the Auth token from the response
    def response = connection.content.text
    def token
    for (def line: response.readLines()) {
      if (line.contains("Auth")) {
        token = line.split("=")[1]
        break
      }
    }

    return token
  }

/**
 * Uses google feed to retrieve more items than in rss
 * @return a list of podcast urls
 */
  def List<Podcast> getPodcastsFromGoogleFeed(token, rssUrl) {

    // concat the google reader url with the rss url to use the google feed
    def googleFeedUrl = "http://www.google.com/reader/atom/feed/${rssUrl}".toURL()

    // get the rss
    def connection = googleFeedUrl.openConnection()
    // add the auth token to the header
    connection.setRequestProperty("Authorization", "GoogleLogin auth=" + token)
    connection.connect()

    def response = connection.content.text
    println "retrieve feed - code: ${connection.responseCode} - message: ${connection.responseMessage} - content: ${response}"

    def feed = new XmlSlurper().parseText(response).declareNamespace(gr: 'http://www.google.com/schemas/reader/atom/')

    // return mp3 urls
    return feed.entry.collect {
      Podcast podcast = new Podcast()
      podcast.url = it.id.@'gr:original-id'.text().toURL()
      podcast.published = toDate(it.published.text())
      return podcast
    }
  }

  /**
   * Converts a string to a date.
   * The string follows the format: 2010-11-25T10:05:00Z
   */
  def Date toDate(String dateAsString) {
    SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    return dateFormatter.parse(dateAsString)
  }

//-----------------------------------------------------------------------------
// inner class
//-----------------------------------------------------------------------------
  class PodcastComparator implements Comparator {
    int compare(Object lhsFile, Object rhsFile) {
      def lhsDate = extractPodcastDate(lhsFile)
      def rhsDate = extractPodcastDate(rhsFile)
      return lhsDate.compareTo(rhsDate)
    }

    /**
     * Extracts podcast date
     */
    def extractPodcastDate(file) {
      def dateAsString = file.getName().split("-")[1]
      def parser = new SimpleDateFormat("dd.MM.yyyy")
      parser.parse(dateAsString)
    }

  }
}
