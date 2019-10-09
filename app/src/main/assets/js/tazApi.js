var tazApi = (function() {

	var menuStatus = false;

	return (function() {

        function getConfiguration(name, callback) {
            console.log("getconfiguration " + name + " " + callback);
            callback(ANDROIDAPI.getConfiguration(name));
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

        function onGesture(gesture, x, y) {
            console.log("onGesture " + gesture + " " + x + "," + y);
        }

		return {
			getConfiguration : getConfiguration,
			setConfiguration : setConfiguration,
			pageReady : pageReady,
			nextArticle : nextArticle,
			previousArticle : previousArticle,
			openUrl : openUrl,
			onGesture: onGesture
		}
	}());
}());
