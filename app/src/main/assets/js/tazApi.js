var tazApi = (function() {

	var menuStatus = false;

	return (function() {

        function getConfiguration(name, callback) {
            console.log("getconfiguration " + name + " " + callback);
            if (typeof(name) == "string") {
                console.log("get configuration with a single string");
                callback(ANDROIDAPI.getConfiguration(name));
            } else { /* name is supposed to be string array */
                console.log("get configuration with a string array");
                for (i in name){
                    callback(ANDROIDAPI.getConfiguration(name[i]));
                }
            }
        }

        function setConfiguration(name, value) {
            console.log("setconfiguration " + name + " " + value);
            ANDROIDAPI.setConfiguration(name, value)
        }

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

		return {
			getConfiguration : getConfiguration,
			setConfiguration : setConfiguration,
			pageReady : pageReady,
			nextArticle : nextArticle,
			previousArticle : previousArticle,
			openUrl : openUrl
		}
	}());
}());
