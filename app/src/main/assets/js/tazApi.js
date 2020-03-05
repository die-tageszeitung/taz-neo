var tazApi = (function() {

	var menuStatus = false;

	return (function() {

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

		return {
			getConfiguration : getConfiguration,
			setConfiguration : setConfiguration,
			pageReady : pageReady,
			nextArticle : nextArticle,
			previousArticle : previousArticle,
			openUrl : openUrl,
			injectCss: injectCss
		};
	}());
}());
