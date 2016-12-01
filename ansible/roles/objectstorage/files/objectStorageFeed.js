/**
 *   Whisk system action to fire the triggers based on the changes on the container.
 *  @param {string} dbProvider - type of the database
 *  @param {string} dbUsername - username used to access the database
 *  @param {string} dbPassword - password of the username
 *  @param {string} dbHost - host of the database
 *  @param {string} dbPort - port of the database
 *  @param {string} databaseName - name of the databaseName to be accessed
 *  @param {string} container_id - ID of the object storage container
 *  @param {string} lifecycleEvent - type of the changes on the container, either create or delete
 */

var params = {"dbProvider": "Cloudant",
		"dbUsername": "houshengbo",
		"dbPassword": "000000000",
		"databaseName": "objectstoragetrigger",
		"designDoc": "objectstoragetrigger",
		"viewNameTrigger": "by_trigger_and_container",
		"endpoint": "endpointofservice",
		"port": "443",
		
		"osService": "9.37.247.209",
		"osPort": "8080",
		"osUsername": "admin:admin",
		"osPassword": "admin",
		"containerID": "test_container.10998",
		//"containerID": "test_container.11252",

		"policyID": "1",
		"policy": '{"id":"one","transport":"kafka","topic":"python"}',
		"lifecycleEvent": "CREATE",
		//"lifecycleEvent": "DELETE",
		"supportedEventType": ["create"],
		"triggerName": "/namespace/triggerName3",
		"triggerAuthkey": "apiKey"}

//deleteDatabase(params);
//main(params)

function main(params) {
	// Get the database information.
	databaseParams = {};
	databaseParams["dbProvider"] = params.dbProvider;
	databaseParams["dbUsername"] = params.dbUsername;
	databaseParams["dbPassword"] = params.dbPassword;
	databaseParams["dbHost"] = params.dbHost;
	databaseParams["dbPort"] = params.dbPort;
	databaseParams["dbProtocol"] = params.dbProtocol || "https";
	databaseName = params.databaseName || "objectstoragetrigger";
	var nano = getDatabaseConnection(databaseParams);

	// Get the view information.
	view = {};
	view["viewNameTrigger"] = params.viewNameTrigger || "by_trigger_and_container";
	view["designDoc"] = params.designDoc || "objectstoragetrigger";

	// Get the input information of the trigger.
    // Endpoint of the whisk service, where trigger is published.
    if (!(params.endpoint)) {
    	return console.log('You must define an endpoint.');
    }

    // Port of the endpoint.
    if (!(params.port)) {
    	return console.log('You must define a port.');
    }
    
    // Container ID of the object storage as the event source.
    if (!(params.containerID)) {
    	return console.log('You must define a container id.');
    }

    if (!(params.osService)) {
    	return console.log('You must define a service IP or url for the object storage service.');
    }

    var host = 'https://' + params.endpoint + ':' + params.port;
    var triggerObj = parseQName(params.triggerName);
    var triggerUrl = host + '/api/v1/namespaces/' + triggerObj.namespace + '/triggers/' + triggerObj.name;

    var osConnection = {};
    osConnection["containerID"] = params.containerID;
    osConnection["osService"] = params.osService;
    osConnection["osPort"] = params.osPort || "8080";
    osConnection["osUsername"] = params.osUsername;
    osConnection["osPassword"] = params.osPassword;
    osConnection["url"] = "http://" + osConnection["osService"] + ":" + osConnection["osPort"] + "/auth/v1.0";
    osConnection["container_url"] = "http://" + osConnection["osService"] + ":" + osConnection["osPort"] + "/v1/AUTH_admin/" + osConnection["containerID"]; 

    var policy = {};
    policy["policyID"] = params.policyID || getDefaultPolicyID();
    policy["policy"] = params.policy;
    
	var input = {};
    input["containerID"] = params.containerID;
    input["supportedEventType"] = params.supportedEventType || ["create"];
    input["triggerName"] = params.triggerName;
    input["triggerUrl"] = triggerUrl;
    // To be replaced with whisk.getAuthKey()
    input["triggerAuthkey"] = params.triggerAuthkey;

    // lifecycleEvent is either one of [ create, delete ], default to CREATE.
    var lifecycleEvent = (params.lifecycleEvent || 'CREATE').trim().toUpperCase();
    containerPolicyConfigued(osConnection, policy, lifecycleEvent)
		.then(function(result) {
			processTrigger(nano, input, databaseName, params, lifecycleEvent);
		})
		.catch(function(result) {
			console.log("Failed to configure the policy of the container.");
		});
}

function getDefaultPolicyID() {
	return "1";
}

function containerPolicyConfigued(osConnection, policy, lifecycleEvent) {
	var promise = new Promise(function(resolve, reject) {
		if (lifecycleEvent == "DELETE") {
			// There is no need to do anything at this moment, since the container policy can be shared by
			// different triggers.
			resolve(true);
		}
		else {
			var token = "";
			// Create the policy for the container.
			getOSTokenPromise(osConnection)
				.then(function(result) {
					token = result;
					console.log("get the policy");
					return getOSContainerPolicyPromise(osConnection, token, policy);
				})
				.then(function(result) {
					if (result == undefined) {
						console.log("create the policy");
						return createOSContainerPolicyPromise(osConnection, token, policy);
					}
					resolve(true);
				}).then(function(result) {
					if (result != undefined) {
						resolve(true);
					}
					reject(false);
				})
				.catch(function(result) {
					reject(false);
				});
		}
	});
	return promise;
}

function createOSContainerPolicyPromise(osConnection, token, policy) {
	var promise = new Promise(function(resolve, reject) {
		var request = require("request");
		var policyKey = "X-Container-Meta-Notification-Policy" + policy["policyID"];
		var options = {
				url: osConnection["container_url"],
		        headers: {
		        	'X-Auth-Token': token,
		        }
		};
		options.headers[policyKey] = policy["policy"];
		request.post(options, function(error, response, body) {
			if (!error && response.statusCode === 204) {
                resolve(policy["policy"]);
            }
			else {
				reject("failed to create the container policy.");
			}
	    });
	});
	return promise;
}

function getOSContainerPolicyPromise(osConnection, token, policy) {
	var promise = new Promise(function(resolve, reject) {
		var request = require("request");
		var options = {
				url: osConnection["container_url"],
		        headers: {
		        	'X-Auth-Token': token
		        }
		};
		request.head(options, function(error, response, body) {
			console.log("error is "+ error);
			if (!error && response.statusCode === 204) {
				var policyKey = "x-container-meta-notification-policy" + policy["policyID"];
				console.log("response policy is "+ response["headers"][policyKey]);
                resolve(response["headers"][policyKey]);
            }
			else {
				reject("The container is not available.");
			}
	    });
	});
	return promise;
}

function getOSTokenPromise(osConnection) {
	var promise = new Promise(function(resolve, reject) {
		var request = require("request");
		var options = {
				url: osConnection["url"],
		        headers: {
		        	'X-Storage-User': osConnection["osUsername"],
		        	'X-Storage-Pass': osConnection["osPassword"]
		        }
		
		};
		request.get(options, function(error, response, body) {
			if (!error && response.statusCode === 200) {
                resolve(response["headers"]["x-auth-token"]);
            }
			else {
				reject("Unable to get the token for the object storage.");
			}
	    });
	});
	return promise;
}

function getDatabaseConnection(params) {
	var dbProvider = params.dbProvider;
	var dbUsername = params.dbUsername;
	var dbPassword = params.dbPassword;
	var dbHost = params.dbHost;
	var dbPort = params.dbPort;
	var dbProtocol = params.dbProtocol;

	var database = null;
	if (dbProvider == 'Cloudant') {
		database = {
			url: dbProtocol + "://" + dbUsername + ":" + dbPassword + "@" + dbUsername + ".cloudant.com"
		};
	} else if (dbProvider == 'CouchDB') {
		database = {
			url: dbProtocol + "://" + dbUsername + ":" + dbPassword + "@" + dbHost + ":" + dbPort
		};
	}
	var nano = require('nano')(database.url);
	return nano
}

function parseQName(qname) {
    var parsed = {};
    var delimiter = '/';
    var defaultNamespace = '_';
    if (qname && qname.charAt(0) === delimiter) {
        var parts = qname.split(delimiter);
        parsed.namespace = parts[1];
        parsed.name = parts.length > 2 ? parts.slice(2).join(delimiter) : '';
    } else {
        parsed.namespace = defaultNamespace;
        parsed.name = qname;
    }
    return parsed;
}

function destroyDatabase(err, body) {
	if (!err) {
	    this.nano.db.destroy(this.databaseName);
	    console.log('The database ' + this.databaseName + ' has been deleted.');
	}
	else {
		console.log('The database ' + this.databaseName + ' does not exist.');
	}
}

function checkDatabase(nano, params, callback) {
	var databaseName = params.databaseName;
	nano.db.get(databaseName, callback);
}

function deleteDatabase(params) {
	var nano = getDatabaseConnection(params);
	checkDatabase(nano, params, destroyDatabase.bind({nano: nano, databaseName: params.databaseName}))
}

function insertViewPromise(db, views, designDoc, viewName) {
	var promise = new Promise(function(resolve, reject) { 
		db.insert(views, designDoc, function (error, response) {
			if (!error) {
				console.log("The view " + viewName + " has been inserted.");
				resolve(response);
			}
			else {
				console.log("Failed to insert the view " + viewName + ".");
				reject(error);
			}
		});
	});
	
	return promise;
}

function getViewPromise(db, params) {
	var viewName = params.viewNameTrigger;
	var designDoc = "_design/" + params.designDoc;
	var promise = new Promise(function(resolve, reject) { 
		db.get(designDoc, function(error, body) {
			if (!error) {
				if (body["views"][params.viewNameTrigger] != null) {
					console.log("The view " + viewName + " has been available.");
					resolve(body);
				}
				else {
					reject("The view " + viewName + " does not exist.");
				}
			}
			else {
				console.log("The view " + viewName + " does not exist.");
				reject("The view " + viewName + " does not exist.");
			}
		});
	});
	
	return promise;
}

function getDatabasePromise(nano, databaseName) {
	var promise = new Promise(function(resolve, reject) {
		nano.db.get(databaseName, function(err, body) {
			if (!err) {
				resolve(body);
			}
			else {
				reject(err);
			}
		});
	});
	return promise;
}

function createViewPromise(nano, databaseName, input) {
	var promise = new Promise(function(resolve, reject) {
		nano.db.create(databaseName, function(err, body) {
			if (!err) {
				resolve(body);
			}
			else {
				reject(err);
			}
		});
	});
	return promise;	
}

function createDatabasePromise(nano, databaseName) {
	var promise = new Promise(function(resolve, reject) {
		nano.db.create(databaseName, function(err, body) {
			if (!err) {
				console.log('The database' + databaseName + ' is created.')
				resolve(body);
			}
			else {
				console.log('Failed to create the database' + databaseName + '.')
				reject(err);
			}
		});
	});
	return promise;
}

function processTrigger(nano, input, databaseName, viewParams, lifecycleEvent) {
	var db = null;
	getDatabasePromise(nano, databaseName)
		.then(function(result) {
			return result;
		}, function(err) {
			// If the database is not available, create it.
			return createDatabasePromise(nano, databaseName);
		})
		.then(function(result) {
			// When the database is available or created, check if the view is available.
			db = nano.db.use(databaseName);
			return getViewPromise(db, viewParams);
		}, function(err) {
			// If the database is not available or fails to be created
			return err;
		})
		.then(function(result) {
			// If the view exists, return the result.
			return result;
		}, function(err) {
			// If the view does not exist, create the view.
			var views = {};
			var view_trigger_container = {};
			view_trigger_container[viewParams.viewNameTrigger] = {"map": function(doc) { if (doc.containerID) { emit(doc.containerID, doc); } } };
			views["views"] = view_trigger_container;
			var designDoc = "_design/" + viewParams.designDoc;
			return insertViewPromise(db, views, designDoc, viewParams.viewNameTrigger) 
		})
		.then(function(result) {
			// If the view is available, return the result.
			return getTriggerPromise(nano, input, databaseName);
		}, function(err) {
			// If the view is not available or fails to be created, return the error.
			console.log('err is ' + err);
			return err;
		})
		.then(function(result) {
			// If the trigger is available, update the trigger.
			if (lifecycleEvent == 'CREATE') {
				input["_rev"] = result._rev;
				return insertTriggerPromise(nano, input, databaseName);
			}
			else if (lifecycleEvent == 'DELETE') {
				return deleteTriggerPromise(nano, input, result._rev, databaseName);
			}
		}, function(err) {
			// If the trigger is not available, insert the trigger.
			if (lifecycleEvent == 'CREATE') {
				return insertTriggerPromise(nano, input, databaseName);
			}
			else if (lifecycleEvent == 'DELETE') {
				return console.log("The trigger does not exist. No need to delete.");
			}
		});
}

function getTriggerPromise(nano, input, databaseName) {
	var db = nano.db.use(databaseName);
	var promise = new Promise(function(resolve, reject) {
		db.get(input["triggerName"], function (error, res) {
			if(!error) {
				console.log('The trigger ' + input["triggerName"]  + ' is available.');
				resolve(res);
			} else {
				console.log('The trigger ' + input["triggerName"]  + ' does not exist.');
				reject(error);
			}
		});
	});
	return promise;
}

function insertTriggerPromise(nano, input, databaseName) {
	var db = nano.db.use(databaseName);
	var promise = new Promise(function(resolve, reject) {
		db.insert(input, input["triggerName"], function(error, res) {
			if(!error) {
				console.log('The trigger ' + input["triggerName"]  + ' has been saved into the database.');
				resolve(res);
			} else {
				console.log('The trigger ' + input["triggerName"]  + ' failed to save in the database.');
				reject(error);
			}
		});
	});
	return promise;
}

function deleteTriggerPromise(nano, input, rev, databaseName) {
	var db = nano.db.use(databaseName);
	var promise = new Promise(function(resolve, reject) {
		db.destroy(input["triggerName"], rev, function(err, body) {
			console.log(body);
			if (!err)
				console.log('The record about the trigger ' + input["triggerName"] + ' has been removed.');
			else
				console.log('The record about the trigger ' + input["triggerName"] + ' failed to remove.');
		});
	});
	return promise;
}
