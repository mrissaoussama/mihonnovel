

i still get TextView does not support text selection. Selection cancelled.

I found a potential cause for why progress doesnt update, if while scrolling, and for example, threshold is set to 30, if user scrolls past 30, progress percentage/bar jumps to 100 percent
and when switching to next chapter, the title header doesnt update, so there is a a desync or disconnection somewhere , a common but not unique pattern is that every 2 chapters read, the 3rd isnt set as read or read progress updated
the issue might happen at threshold percent set, when getting to that/bypassing it(and probably chapter loads) the progress has a sudden jump to 100 percent before going back to regular percent if user scrolls again

NovelViewer: Saving progress 32% for chapter
NovelProgress: Saved 32% for Chapter 5.
NovelViewer: page ready, text.length=6008
NovelViewer: appending chapter 52859625
NovelViewer: Displaying chapter 52859625, infinite scroll enabled: true, loaded count: 3
NovelViewer: Removed distant chapter, adjusted scroll by -6272
NovelViewer: Successfully appended next chapter Chapter 6. Dungeon Boss
hiddenapi: Accessing hidden method Landroid/widget/Editor;->stopTextActionMode()V (runtime_flags=0, domain=platform, api=max-target-o) from Leu/kanade/tachiyomi/ui/reader/viewer/text/NovelViewer; (domain=app) using reflection: denied
NovelViewer: Chapter changed from index 0 to 1 (Chapter 5.=)

figure it out 
in #file:NovelDownloadPreferences.kt , in extensions overrides, make sure:
extension list there is sorted
overrides actually work and enforced 
in #library
in #file:LibraryScreenModel.kt , for some reason, unread/read/d, badges do not update for related updated manga
2026-02-26 22:36:07.401 15861-19669 Downloader              app.tsundoku.dev                     D    -> Fetched text, length=12690
2026-02-26 22:36:07.401 15861-19669 Downloader              app.tsundoku.dev                     D    -> Saving text to 001.html
2026-02-26 22:36:07.401 15861-19669 Downloader              app.tsundoku.dev                     D    -> Embedding images in chapter HTML
2026-02-26 22:36:07.402 15861-19669 UndispatchedCoroutine   app.tsundoku.dev                     D  ChapterImageEmbedder: Found 0 images to process
2026-02-26 22:36:12.130 15861-15893 WM-Processor            app.tsundoku.dev                     D  Processor cancelling a769401b-3cfc-453a-b4e7-27a6a3a667ad
find out why, you can use similar logic to how the manga libraryitem itself is updated on title change, category change ect are updated in memory, curretnly, even reload library button doesnt refresh that, i need to do a ful lrestart. manga aggregaes column when udated, dont notify the in memory library, or it detects nothing changed and throws away the event.

the file #file:HttpPageLoader.kt was at firt, supposed to work for manga only, but for somereason, on certain sources/multisources for manga, it broke after adding support for novels. certain implementations like MadTheme (i do not want to edit the extension, issue is from app) doesnt load, throws this 
   if (page.url.isNotBlank()) {
                    page.imageUrl = source.getImageUrl(page)
                } else {
                    throw IllegalStateException("Page ${page.index} has no URL and no image URL")
                }
                fix this, you can checkout to branch mihon or merge/upstream-mihon to compare the impl and figure out how to properly isolate it
                i put a debug poiint on             if (page.imageUrl.isNullOrEmpty()) {
 and for some reason the image url is null. same for imageurl, image properlies in page param
 even though in some manga extensions they work, same for url and uri? Pager first layout
onReaderPageSelected: 1/49
visibilityChanged oldVisibility=true newVisibility=false
switch root view (mImeCallbacks.size=0)
app.tsundoku.dev:adb96aac: onRequestHide at ORIGIN_SERVER reason HIDE_UNSPECIFIED_WINDOW fromUser false
setTopOnBackInvokedCallback (unwrapped): android.app.Activity$$ExternalSyntheticLambda0@69acf29


 for certain manga extensions using #file:HttpSource.kt , sometimes i get illegealargument exceptiom expected url scheme http or https but no scheme was found for " blank here" verify that for manga, behavior matches mihon/mihonapp brqnch 
 also verify that novels supports multiple images for both webview and novelviewert, and 

 
 Auto chapter downloading seems to work fine with infinite scrolling, but breaks with any other mechanism of switching chapters? check auto_download_while_reading option to ssee what is going on, verify it would work with novel modes, both with inf scroll and it off 
 
 it appears to me that telling the backup system not to back up novels does not actually work.
 it needs to exclude isnovel=true 
 Global update > Categories isn't restored by backup/restore (exclude/include option in #libraryscreensettings)
 
 in novel viewerr #novelviewer.kt textview , images are cut off to fit width, but i want to resize them so they all fit the width, not be cut off 
 also fix block images and video in #novelpage.kt not being shared between webvierwer and textview 

 on first ch load, textview, it doesnt load images
 NovelViewer: Displaying chapter 52855581, infinite scroll enabled: true, loaded count: 0
NovelViewer: Restoring progress, savedProgress=94, isRead=true for Volume 1...
hiddenapi: Accessing hidden field Landroid/widget/TextView;->mEditor:Landroid/widget/Editor; (runtime_flags=0, domain=platform, api=unsupported) from Leu/kanade/tachiyomi/ui/reader/viewer/text/NovelViewer; (domain=app) using reflection: allowed
hiddenapi: Accessing hidden method Landroid/widget/Editor;->stopTextActionMode()V (runtime_flags=0, domain=platform, api=max-target-o) from Leu/kanade/tachiyomi/ui/reader/viewer/text/NovelViewer; (domain=app) using reflection: denied
💾 Successful (DISK) - https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEgBZdQlv19WdlZ5i5UZtTZ_w-iTeM4-GtcndnyCHziqMa0TJwghJFSmA460Maunh0JRKVKH9KkysUNd22z1n7rJFBC-5UW-AT7JTAgqsfWvGQdJt8f7i5hZJzlfnJVK9SRpIhkiQ-VzR0tqSpCb3lNw8G8fiV5bUrBOVLRzFhDH3fALxQCIRLriA27MkNP-/s1600/kuchie-001.jpg
💾 Successful (DISK) - https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEhdL0P_W1n0yO5uEfah-AoCbQbsuFdbNK94DLl9TjaovOwZQcULi7lwqv6JgILDOtrt0mqmJ-eHGBv9XIaXlTxm1sG01CRJILMl9nMeIuamo_HvGBO7b0wLMoGDBhyphenhyphen0XsZECSHqNm49my8MLeVUYp0Ey6DOATBub5vFjRuBliGdRBojDyMYq5lucH9e6zOh/s1600/kuchie-004-005.jpg
💾 Successful (DISK) - https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEjFo-NBqZQ0XoEDFqfVE0ZR0yQt7C8i3fcUXOo-DIpHi0-rvwtJQoexScWouuBQimWYsoB_pp47TVmnujin3lNGxSjZlRXC8yrGOaI52ypG57dmkv0qjLMF-veU7ks6BrlLIGCXH3GIMTMMiq3NHGPaowr4P_CvyQbodN3WtHPf0SkmkqE6fhm5NVmQ60bo/s1600/kuchie-002-003.jpg
💾 Successful (DISK) - https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEhLRu-pZgjfXDoGCQWdJC1osBVDe4JHwSsko2tVLfucVwxlCPrNDn9NRgowVKZv8lNroq-BpnLRn378BNP5g4sGuyr9ht0JWNenH3HdvZnjVqAgUKr4TCJTq5n7lYT1PqcR5hQUXD4g_ez0uad21kb14S75fXX7rJSgWWNF4v1OzCmxN2sK68P2zQhBN1ja/s1600/cover.jpeg
💾 Successful (DISK) - https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEhnqT3Xt5NmylEZztPlZxW-kK-EF94TqQKUVm6x8DZiLbfLBdG8r7Cop2sqMCh1tZ-cmxChMOVwV4teDk2rl4N_wXHI7ciyaBKQ_pdX68msCxRwMium2LzMC6EA0mUhG59TIZOYrmAXdlF-SH10xLcn2sdE7tLHJCB8_crF5K89hwi9UhuQZZTFj9UtwaXr/s1600/kuchie-006-007.jpg
IncrementDisableThreadFlip blocked for 14.153ms
Background concurrent mark compact GC freed 48MB AllocSpace bytes, 281(17MB) LOS objects, 15% free, 256MB/304MB, paused 770us,14.126ms total 899.940ms

i have to go back and forth for it to load the cached images. webview works fine
when cached: they render 
NovelViewer: Displaying chapter 52855581, infinite scroll enabled: true, loaded count: 0
NovelViewer: Restoring progress, savedProgress=94, isRead=true for Volume 1...
🧠 Successful (MEMORY_CACHE) - https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEhLRu-pZgjfXDoGCQWdJC1osBVDe4JHwSsko2tVLfucVwxlCPrNDn9NRgowVKZv8lNroq-BpnLRn378BNP5g4sGuyr9ht0JWNenH3HdvZnjVqAgUKr4TCJTq5n7lYT1PqcR5hQUXD4g_ez0uad21kb14S75fXX7rJSgWWNF4v1OzCmxN2sK68P2zQhBN1ja/s1600/cover.jpeg
🧠 Successful (MEMORY_CACHE) - https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEgBZdQlv19WdlZ5i5UZtTZ_w-iTeM4-GtcndnyCHziqMa0TJwghJFSmA460Maunh0JRKVKH9KkysUNd22z1n7rJFBC-5UW-AT7JTAgqsfWvGQdJt8f7i5hZJzlfnJVK9SRpIhkiQ-VzR0tqSpCb3lNw8G8fiV5bUrBOVLRzFhDH3fALxQCIRLriA27MkNP-/s1600/kuchie-001.jpg
🧠 Successful (MEMORY_CACHE) - https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEjFo-NBqZQ0XoEDFqfVE0ZR0yQt7C8i3fcUXOo-DIpHi0-rvwtJQoexScWouuBQimWYsoB_pp47TVmnujin3lNGxSjZlRXC8yrGOaI52ypG57dmkv0qjLMF-veU7ks6BrlLIGCXH3GIMTMMiq3NHGPaowr4P_CvyQbodN3WtHPf0SkmkqE6fhm5NVmQ60bo/s1600/kuchie-002-003.jpg
🧠 Successful (MEMORY_CACHE) - https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEhdL0P_W1n0yO5uEfah-AoCbQbsuFdbNK94DLl9TjaovOwZQcULi7lwqv6JgILDOtrt0mqmJ-eHGBv9XIaXlTxm1sG01CRJILMl9nMeIuamo_HvGBO7b0wLMoGDBhyphenhyphen0XsZECSHqNm49my8MLeVUYp0Ey6DOATBub5vFjRuBliGdRBojDyMYq5lucH9e6zOh/s1600/kuchie-004-005.jpg
🧠 Successful (MEMORY_CACHE) - https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEhnqT3Xt5NmylEZztPlZxW-kK-EF94TqQKUVm6x8DZiLbfLBdG8r7Cop2sqMCh1tZ-cmxChMOVwV4teDk2rl4N_wXHI7ciyaBKQ_pdX68msCxRwMium2LzMC6EA0mUhG59TIZOYrmAXdlF-SH10xLcn2sdE7tLHJCB8_crF5K89hwi9UhuQZZTFj9UtwaXr/s1600/kuchie-006-007.jpg
hiddenapi: Accessing hidden method Landroid/widget/Editor;->stopTextActionMode()V (runtime_flags=0, domain=platform, api=max-target-o) from Leu/kanade/tachiyomi/ui/reader/viewer/text/NovelViewer; (domain=app) using reflection: denied
Background concurrent mark compact GC freed 49MB AllocSpace bytes, 472(22MB) LOS objects, 15% free, 265MB/313MB, paused 783us,14.767ms total 869.096ms
A resource failed to call close. 
Background concurrent mark compact GC freed 53MB AllocSpace bytes, 411(22MB) LOS objects, 15% free, 266MB/314MB, paused 892us,14.976ms total 858.477ms
Background concurrent mark compact GC freed 58MB AllocSpace bytes, 465(25MB) LOS objects, 15% free, 259MB/307MB, paused 1.156ms,14.800ms total 863.461ms
Background concurrent mark compact GC freed 52MB AllocSpace bytes, 490(22MB) LOS objects, 15% free, 261MB/309MB, paused 716us,14.780ms total 858.980ms
Backup: Processed 900/7606 batches
Background concurrent mark compact GC freed 55MB AllocSpace bytes, 489(24MB) LOS objects, 15% free, 257MB/305MB, paused 838us,15.222ms total 861.124ms


 

 also, there is still the issue of wrong chapters being tracked/saved progress too 
 NovelViewer: page ready, text.length=7755
NovelViewer: appending chapter 552201
NovelViewer: Displaying chapter 552201, infinite scroll enabled: true, loaded count: 3
NovelViewer: Removed distant chapter, adjusted scroll by -5966
NovelViewer: Successfully appended next chapter Chapter 264
hiddenapi: Accessing hidden method Landroid/widget/Editor;->stopTextActionMode()V (runtime_flags=0, domain=platform, api=max-target-o) from Leu/kanade/tachiyomi/ui/reader/viewer/text/NovelViewer; (domain=app) using reflection: denied
NovelViewer: Chapter changed from index 0 to 1 (Chapter 266)
NovelViewer: Marking chapter 1 as 100% (moved forward)
Setting /novel/yuri-empire/chapter-264 as active (no reload)
NovelViewer: Chapter changed from index 1 to 2 (Chapter 264)
NovelProgress: Saved 100% for Chapter 266
NovelViewer: Saving progress 18% for chapter
NovelProgress: Saved 18% for Chapter 264
NovelViewer: Saving progress 19% for chapter
NovelProgress: Saved 19% for Chapter 264
NovelViewer: Saving progress 20% for chapter
NovelProgress: Saved 20% for Chapter 264
NovelViewer: Saving progress 21% for chapter
NovelProgress: Saved 21% for Chapter 264
MangaRepositoryImpl.getLibraryManga: Query completed in 41216ms, returned 152092 items
GetLibraryManga: Refresh complete in 41219ms, 152092 items
NovelProgress: Saved 95% for Chapter 269
NovelProgress: Saved 96% for Chapter 268
NovelProgress: Saved 95% for Chapter 267
NovelProgress: Saved 95% for Chapter 266
Background concurrent mark compact GC freed 32MB AllocSpace bytes, 30(1812KB) LOS objects, 26% free, 134MB/182MB, paused 742us,4.361ms total 482.639ms

the above progress is wrong, it is missing /ignoring some chapters,mixing chapters ect. race condition????

more logs 
NovelViewer: Saving progress 59% for chapter
NovelProgress: Saved 59% for Chapter 269
NovelViewer: Saving progress 60% for chapter
NovelProgress: Saved 60% for Chapter 269
ReaderViewModel: prepare finished for chapter 552205/Chapter 268, state=Loaded(pages=[eu.kanade.tachiyomi.ui.reader.model.ReaderPage@b7b3af9]), pages=1
NovelViewer: prepared next=552205/Chapter 268
NovelViewer: loading page for next 552205, state=Queue

NovelViewer: page ready, text.length=5729
NovelViewer: appending chapter 552205
NovelViewer: Displaying chapter 552205, infinite scroll enabled: true, loaded count: 1
NovelViewer: Successfully appended next chapter Chapter 268
hiddenapi: Accessing hidden method Landroid/widget/Editor;->stopTextActionMode()V (runtime_flags=0, domain=platform, api=max-target-o) from Leu/kanade/tachiyomi/ui/reader/viewer/text/NovelViewer; (domain=app) using reflection: denied
Setting requestedVisibleTypes to -9 (was -528)
NovelViewer: Saving progress 62% for chapter
NovelProgress: Saved 62% for Chapter 269
NovelViewer: Saving progress 66% for chapter
NovelProgress: Saved 66% for Chapter 269
NovelViewer: Saving progress 77% for chapter
NovelProgress: Saved 77% for Chapter 269
NovelViewer: Saving progress 79% for chapter
NovelProgress: Saved 79% for Chapter 269
NovelViewer: Saving progress 87% for chapter
NovelProgress: Saved 87% for Chapter 269
NovelViewer: Saving progress 95% for chapter
NovelViewer: Saving progress 100% for chapter
NovelProgress: Saved 100% for Chapter 269
NovelViewer: Marking chapter 0 as 100% (moved forward)
Setting /novel/yuri-empire/chapter-268 as active (no reload)
NovelViewer: Chapter changed from index 0 to 1 (Chapter 268)
NovelViewer: Saving progress 77% for chapter
NovelViewer: scroll threshold hit (progress=0.77075815 >= 0.3, currentIdx=1, loadedCount=2)
NovelViewer: loadNext starting from anchor=552205/Chapter 268
NovelProgress: Saved 77% for Chapter 268
NovelViewer: Saving progress 80% for chapter
ReaderViewModel: prepare starting for chapter 552204/Chapter 267, state=Wait
Loading pages for Chapter 267
NovelProgress: Saved 80% for Chapter 268

