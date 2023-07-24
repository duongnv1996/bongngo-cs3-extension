// use an integer for version numbers
version = 4


cloudstream {
    // All of these properties are optional, you can safely remove them


    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified
    language = "vi"
    // All of these properties are optional, you can safely remove them

    description = "Fshare 4K Bluray , 2k ,1080P đủ thể loại. Buổi tối ae kéo nhiều hơi lag :3"
    authors = listOf("Blue")

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of avaliable types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf(
            "Movie",
    )
    iconUrl = "https://play-lh.googleusercontent.com/TOTj0uMWp7cXkjXDXkcTZnPigUmpLRRiH956lHRJxStlY7ucxmtwMs_Kr2wOWYm0fSY=w240-h480-rw"
}
