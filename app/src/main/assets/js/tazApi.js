var tazApi = (function() {

	var menuStatus = false;
	var contentResizeObserver = null;

    /*
        @params
        name: String or array of strings
              Name of the desired configuration variable(s)

        callback: closure
                  Receives the value of the configuration variable
                  or a dictionary of values if name was an array of strings

    */
    function getConfiguration(name, callback) {
        console.log("getconfiguration " + name + " " + callback);
        if (typeof(name) == "string") {
            console.log("get configuration with a single string");
            callback(ANDROIDAPI.getConfiguration(name));
        } else { /* name is supposed to be string array */
            console.log("get configuration with a string array");
            var result = {};
            for (i in name){
                result[name[i]] = ANDROIDAPI.getConfiguration(name[i]);
            }
            if (typeof(callback === "function")) {
                callback(result);
            }
        }
    }

    /*
        @params
        name: String or a dictionary of String:String
              The name of the configuration variable to be set
              or a dictionary containing the key:value pairs of configuration variable and new values
        value: String or nil
               New value of the configuration variable or nil if name is a dictionary
    */
    function setConfiguration(name, value) {
        console.log("setconfiguration " + name + " " + value);
        if (typeof(name) == "string") {
            ANDROIDAPI.setConfiguration(name, value);
        } else { /* name is a dict */
            for (i in name) {
                ANDROIDAPI.setConfiguration(i, name[i]);
            }
        }
    }

    /*
        Called after every scroll, the function informs the native code about the current
        scroll position.
        This can be used for tracking reading progress for bookmarked articles for instance.

        @params
        percentSeen (Int 0..100)
            gibt an, wieviel Prozent der Seite gelesen (bzw. hochgescrollt) worden ist.

        position (Int 0..n)
            stellt als opakes Datum die Position des Webviews in einer Seite dar

    */
    function pageReady(percentSeen, position) {
        console.log("pageready " + percentSeen + " " + position);
        ANDROIDAPI.pageReady(percentSeen, position);
    }

    function nextArticle(position) {
        console.log("nextArticle " + position);
        ANDROIDAPI.nextArticle(position);
    }

    function previousArticle(position) {
        console.log("previousArticle " + position);
        ANDROIDAPI.previousArticle(position);
    }

    function openUrl(url) {
        console.log("openUrl "+url);
        ANDROIDAPI.openUrl(url);
    }

    function openImage(name) {
        console.log("openImage "+name);
        ANDROIDAPI.openImage(name);
    }

    function injectCss(encodedCssContent) {
        var parent = document.getElementsByTagName('head').item(0);

        /* remove old previously injected style elements */
        var oldStyleElements = parent.getElementsByTagName('style');

        for (var i = 0; i < oldStyleElements.length; i++) {
            parent.removeChild(oldStyleElements.item(i));
        }

        /* remove current tazApi.css as well, since it sometimes contradicts the injected style */
        var oldStyleLinks = parent.getElementsByTagName('link');
        for (var i = 0; i < oldStyleLinks.length; i++) {
            if (oldStyleLinks.item(i).href.indexOf('tazApi.css') >= 0) {
                parent.removeChild(oldStyleLinks.item(i));
            }
        }

        /* inject new css */
        var style = document.createElement('style');
        style.type = 'text/css';
        /* Tell the browser to BASE64-decode the string */
        style.innerHTML = window.atob(encodedCssContent);
        parent.appendChild(style);
    }

    /**
    * Load the Bookmarks for the current Section.
    * setupBookmarksCallback(articleNames) is called with an array of the names of all
    * bookmarked articles (without the .html suffix).
    */
    function getBookmarks(setupBookmarksCallback) {
        // Find all articles names listed on the current section
        // this is necessary for title sections where the graphql does *not* return all articles
        var articleNames = [];
        var bookmarkStars = document.getElementsByClassName("bookmarkStar");
        for (var i = 0; i < bookmarkStars.length; i++) {
            articleNames.push(bookmarkStars[i].id);
        }

        var bookmarkedArticleNamesJson = ANDROIDAPI.getBookmarkedArticleNames(JSON.stringify(articleNames));

        var bookmarkedArticleNames = JSON.parse(bookmarkedArticleNamesJson);
        setupBookmarksCallback(bookmarkedArticleNames);
    }

    function setBookmark(articleName, isBookmarked, showNotification) {
        ANDROIDAPI.setBookmark(articleName, isBookmarked, showNotification)
    }

    /**
    * Load the enqueued Articles for the current Section.
    * setupEnqueuedCallback(articleNames) is called with an array of the names of all
    * enqueued articles (without the .html suffix).
    */
    function getEnqueuedArticles(setupEnqueuedCallback) {
        // Find all articles names listed on the current section
        // this is necessary for title sections where the graphql does *not* return all articles
        var articleNames = [];
        var enqueuedElements = document.getElementsByClassName("playlistAdd");
        for (var i = 0; i < enqueuedElements.length; i++) {
            articleNames.push(enqueuedElements[i].id);
        }

        var enqueuedArticleNamesJson = ANDROIDAPI.getEnqueuedArticleNames(JSON.stringify(articleNames));

        var enqueuedArticleNames = JSON.parse(enqueuedArticleNamesJson);
        setupEnqueuedCallback(enqueuedArticleNames);
    }

    function setEnqueued(articleName, isEnqueued) {
        ANDROIDAPI.setEnqueued(articleName, isEnqueued)
    }

    function enableArticleColumnMode(heightPx, columnWidthPx, columnGapPx) {
        // If there is already a observer running, we disconnect/stop it
        disconnectContentResizeObserver();

        var content = document.getElementById("content");

        // Enable column mode
        content.classList.add("article--multi-column");

        // Set the #content height to the the webviews parentView height
        content.style.height = heightPx + "px";

        // Overwrite body padding for small (tablet) screens:
        var screenWidth = window.innerWidth;
        if (screenWidth < 660) {
            document.body.classList.add("no-horizontal-padding");
        }

        // (Re-) Set the initial width to the default "auto", so that the first run of the
        // column calculation works correctly (for example when the font size changes)
        content.style.width = "auto";

        // Finally set the requested column width and wait for the contentResizeObserver to settle
        content.style.columnWidth = columnWidthPx + "px";

        // Helper that ensures the callback is called at most once
        var onMultiColumnLayoutReadyCalled = false;
        function callOnMultiColumnLayoutReady(width) {
            if (!onMultiColumnLayoutReadyCalled) {
                onMultiColumnLayoutReadyCalled = true;
                ANDROIDAPI.onMultiColumnLayoutReady(width);
            }
        }

        // Let the css column handling figure out the required size of the container and
        // then set its exact width, so that the outer Android WebView can handle the scrolling.
        function setContentWidthForColumns() {
            // After css has calculated the columns, we can take a look at the scrollWidth to
            // find out the actual number of columns and set the content width sothat
            // it fits the columns exactly.
            var factor = Math.floor(content.scrollWidth / (columnWidthPx + columnGapPx));
            var contentWidth = factor * columnWidthPx + (factor - 1) * columnGapPx;
            var contentWidthString = contentWidth + "px";

            // Stop observing for changes and inform the App that the multi column layout is
            // ready, once the set content width is the same as the calculated one
            // As css only keeps a couple of decimals we define them as the same w´hen their
            // difference is < 1 px
            var currentStyleWidth = NaN;
            if (content.style.width && content.style.width.endsWith("px")) {
                currentStyleWidth = parseFloat(content.style.width);
            }
            var diff = Math.abs(currentStyleWidth - contentWidth);

            if (diff != NaN && diff < 1.0) {
                var lastElement = $('.lastElement')[0];
                var times = Math.ceil((lastElement.offsetLeft + lastElement.clientWidth) / window.innerWidth);
                var widthByLastElement = times * window.innerWidth + columnGapPx;

                if (contentWidth > widthByLastElement) {
                    // Wait for the next cycle to set the width.
                    // If it is set in the same cycle the observer will not be called again
                    setTimeout(function() {
                        content.style.width = widthByLastElement + "px";
                    });
                } else {
                    disconnectContentResizeObserver();
                    // Wait for the next cycle before indicating ready, to give the WebView the
                    // chance to render the changes.
                    setTimeout(function() {
                        callOnMultiColumnLayoutReady(contentWidth);
                    });
                }
            } else {
                // Wait for the next cycle to set the width.
                // If it is set in the same cycle the observer will not be called again
                setTimeout(function() {
                    content.style.width = contentWidthString;
                });
            }
        };

        if (typeof ResizeObserver != "undefined") {
            contentResizeObserver = new ResizeObserver(setContentWidthForColumns);
            contentResizeObserver.observe(content);

            // Ensure that the callback is called eventually after 1s, even if the ResizeObserver didn't settle
            setTimeout(function() {
                disconnectContentResizeObserver();
                callOnMultiColumnLayoutReady(content.style.width);
            }, 1000);

        } else {
            // Fallback for old devices not supporting ResizeObserver (WebView < 64)
            // Set the first content width immediately after this cycle,
            // then wait for 250ms to let the css rendering handle the column width re-set the width.
            // Finally after another 250ms call the the callback.
            ANDROIDAPI.logMissingJsFeature("ResizeObserver");
            setTimeout(setContentWidthForColumns);
            setTimeout(setContentWidthForColumns, 250);
            setTimeout(function() {
                callOnMultiColumnLayoutReady(content.style.width);
            }, 500);
        }
    }

    function disableArticleColumnMode() {
        disconnectContentResizeObserver();
        var content = document.getElementById("content");
        content.style.height = null;
        content.style.width = null;
        content.style.paddingRight = null;
        content.style.columnWidth = null;
        content.classList.remove("article--multi-column");
        document.body.classList.remove("no-horizontal-padding");
    }

    function disconnectContentResizeObserver() {
        if (contentResizeObserver != null) {
            contentResizeObserver.disconnect();
            contentResizeObserver = null;
        }
    }

    function setPaddingRight(padding) {
        var content = document.getElementById("content");
        content.style.paddingRight = parseFloat(getComputedStyle(content).paddingRight) + padding + "px";
    }

    return {
        getConfiguration : getConfiguration,
        setConfiguration : setConfiguration,
        pageReady : pageReady,
        nextArticle : nextArticle,
        previousArticle : previousArticle,
        openUrl : openUrl,
        injectCss: injectCss,
        openImage : openImage,
        getBookmarks: getBookmarks,
        setBookmark: setBookmark,
        getEnqueuedArticles: getEnqueuedArticles,
        setEnqueued: setEnqueued,
        enableArticleColumnMode: enableArticleColumnMode,
        disableArticleColumnMode: disableArticleColumnMode,
        setPaddingRight: setPaddingRight,
    };
}());
