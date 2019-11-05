var tazApi = (function() {

	var menuStatus = false;

	return (function() {

        /**
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
                callback(result);
            }
        }

        /**
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
                    ANDROIDAPI.setConfiguration(i, name[i])
                }
            }
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
