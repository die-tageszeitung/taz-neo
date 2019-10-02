var tazApi = (function() {

	var menuStatus = false;

	return (function() {

        function getConfiguration(name, callback) {
            callback(ANDROIDAPI.getConfiguration(name));
        }

        function setConfiguration(name, value) {
            ANDROIDAPI.setConfiguration(name, value)
        }

		function pageReady(percentSeen, position) {
         	ANDROIDAPI.pageReady(percentSeen, position);
        }

		function nextArticle(position) {
			ANDROIDAPI.nextArticle(position);
		}

		function previousArticle(position) {
			ANDROIDAPI.previousArticle(position);
		}

		function openUrl(url) {
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
