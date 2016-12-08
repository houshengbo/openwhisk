var _ = require('lodash');
var request = require('request');
var Agent = require('agentkeepalive');
var cradle = require('cradle');
var when = require('when');

module.exports = function(
  tid,
  logger,
  app,
  retriesBeforeDelete,
  triggerDB,
  dbProvider,
  triggersFireLimit,
  routerHost
) {

    this.tid = tid;
    this.logger = logger;
    this.app = app;
    this.retriesBeforeDelete = retriesBeforeDelete;
    this.triggerDB = triggerDB;
    this.dbProvider = dbProvider;
    this.triggersLimit = triggersFireLimit;
    this.routerHost = routerHost;

    // Log HTTP Requests
    app.use(function(req, res, next) {
        if (req.url.indexOf('/cloudanttriggers') == 0)
            logger.info(tid, 'HttpRequest',req.method, req.url);
        next();
    });

    this.module = 'utils';
    this.triggers = {};

    var that = this;

    // Add a trigger: listen for changes and dispatch.
    this.createTrigger = function(dataTrigger, retryCount) {

        var method = 'createTriggerCouchDb';

        /*
        var dbUrl = dataTrigger.accounturl;
        if ('null' != dataTrigger.user && 'null' != dataTrigger.pass) {
          dbUrl = 'https://' + dataTrigger.user + ':' + dataTrigger.pass + '@' + dataTrigger.user + '.cloudant.com';
        } else {
          dbUrl = dataTrigger.accounturl;
        }
        */

        // Cleanup connection when trigger is deleted.
        var sinceToUse = dataTrigger.since ? dataTrigger.since : "now";
        //var keepaliveAgent = new Agent({
        //  keepAlive: false
        //});
        //var nanoConnection = require('nano')({
        //  url: 'https://' + dataTrigger.user + ':' + dataTrigger.pass + '@' + dataTrigger.user + '.cloudant.com',
        //  requestDefaults : { agent : keepaliveAgent }
        //});
        var nanoConnection;
        if (dbProvider === 'Cloudant') {          
          nanoConnection = require('nano')('https://' + dataTrigger.user + ':' + dataTrigger.pass + '@' + dataTrigger.user + '.cloudant.com');
        } else if (dbProvider === 'CouchDB') {
          nanoConnection = require('nano')(dataTrigger.accounturl);
        }


        var promise = new Promise(function(resolve, reject) {

          nanoConnection.auth(dataTrigger.user, dataTrigger.pass, function (err, body, headers) {

            if (err) {
              reject(err);
            } else {
              var triggeredDB = nanoConnection.use(dataTrigger.dbname);
              // Listen for changes on this database.
              var feed = triggeredDB.follow({since: sinceToUse, include_docs: true});

              dataTrigger.feed = feed;
              that.triggers[dataTrigger.id] = dataTrigger;

              feed.on('change', function (change) {
                  var triggerHandle = that.triggers[dataTrigger.id];
                  logger.info(tid, method, 'Got change from', dataTrigger.dbname, change);
                  if(triggerHandle && triggerHandle.triggersLeft > 0 && triggerHandle.retriesLeft > 0) {
                      try {
                          that.invokeWhiskAction(dataTrigger.id, change);
                      } catch (e) {
                          logger.error(tid, method, 'Exception occurred in callback', e);
                      }
                  }
              });

              feed.follow();

              resolve(feed);
            }

          });

        }).then(function(feed) {

          return new Promise(function(resolve, reject) {

            feed.on('error', function (err) {
                logger.error(tid, method,'Error occurred for trigger', dataTrigger.id, '(db ' + dataTrigger.dbname + '):', err);
                // revive the feed if an error ocurred for now
                // the user should be in charge of removing the feeds
                logger.info(tid, "attempting to recreate trigger", dataTrigger.id);
                that.deleteTrigger(dataTrigger.id);
                dataTrigger.since = "now";
                if (retryCount > 0) {
                    var addTriggerPromise = that.createTrigger(dataTrigger, (retryCount - 1));
                    addTriggerPromise.then(function(trigger) {
                        logger.error(tid, method, "Retry Count:", (retryCount - 1));
                        resolve(trigger);
                    }, function(err) {
                        reject(err);
                    });
                } else {
                    logger.error(tid, method, "Trigger's feed produced too many errors. Deleting the trigger", dataTrigger.id, '(db ' + dataTrigger.dbname + ')');
                    reject({
                        error: err,
                        message: "Trigger's feed produced too many errors. Deleting the trigger " + dataTrigger.id
                    });
                }
            });

            feed.on('confirm', function (dbObj) {
                logger.info(tid, method, 'Added cloudant data trigger', dataTrigger.id, 'listening for changes in database', dataTrigger.dbname);
                resolve(dataTrigger);
            });

          });

        }, function(err){
          reject(err);
        }).then(function(trgr){
          return Promise.resolve(trgr);
        }, function(err){
          return Promise.reject(err);
        });

        return promise;

    };

    // Add a trigger: listen for changes and dispatch.
    this.createTriggerCloudantDb = function(dataTrigger, retryCount) {

        var method = 'createTrigger';

        var connection = new(cradle.Connection)(dataTrigger.accounturl, 443, {
            auth: {
                username: dataTrigger.user,
                password: dataTrigger.pass
            }
        });
        var db = connection.database(dataTrigger.dbname);
        var sinceToUse = dataTrigger.since ? dataTrigger.since : "now";
        var feed = db.changes({include_docs: dataTrigger.includeDoc, since: sinceToUse});

        ///
        dataTrigger.feed = feed;
        that.triggers[dataTrigger.id] = dataTrigger;

        feed.on('change', function (change) {
            var triggerHandle = that.triggers[dataTrigger.id];
            logger.info(tid, method, 'Got change from', dataTrigger.dbname, change);
            if(triggerHandle && triggerHandle.triggersLeft > 0 && triggerHandle.retriesLeft > 0) {
                try {
                    that.invokeWhiskAction(dataTrigger.id, change);
                } catch (e) {
                    logger.error(tid, method, 'Exception occurred in callback', e);
                }
            }
        });

        var feedPromise = new Promise(function(resolve, reject) {
          feed.on('error', function (err) {
              logger.error(tid, method,'Error occurred for trigger', dataTrigger.id, '(db ' + dataTrigger.dbname + '):', err);
              // revive the feed if an error ocurred for now
              // the user should be in charge of removing the feeds
              logger.info(tid, "attempting to recreate trigger", dataTrigger.id);
              that.deleteTrigger(dataTrigger.id);
              dataTrigger.since = "now";
              if (retryCount > 0) {
                  var addTriggerPromise = that.createTrigger(dataTrigger, (retryCount - 1));
                  addTriggerPromise.then(function(trigger) {
                      logger.error(tid, method, "Retry Count:", (retryCount - 1));
                      resolve(trigger);
                  }, function(err) {
                      reject(err);
                  });
              } else {
                  logger.error(tid, method, "Trigger's feed produced too many errors. Deleting the trigger", dataTrigger.id, '(db ' + dataTrigger.dbname + ')');
                  reject({
                      error: err,
                      message: "Trigger's feed produced too many errors. Deleting the trigger " + dataTrigger.id
                  });
              }
          });

          feed.on('confirm', function (dbObj) {
              logger.info(tid, method, 'Added cloudant data trigger', dataTrigger.id, 'listening for changes in database', dataTrigger.dbname);
              resolve(dataTrigger);
          });
        });

        feed.follow();

        return feedPromise;

    };

    this.initTrigger = function (obj, id) {

        logger.info(tid, 'initTrigger', obj);
        var includeDoc = ((obj.includeDoc == true || obj.includeDoc.toString().trim().toLowerCase() == 'true')) || "false";
        var trigger = {
            id: id,
            accounturl: obj.accounturl,
            dbname: obj.dbname,
            user: obj.user,
            pass: obj.pass,
            includeDoc: includeDoc,
            apikey: obj.apikey,
            since: obj.since,
            callback: obj.callback,
            maxTriggers: obj.maxTriggers,
            triggersLeft: obj.maxTriggers,
            retriesLeft: retriesBeforeDelete
        };

        return trigger;

    };

    this.initAllTriggers = function () {

        var method = 'initAllTriggers';
        logger.info(tid, that.module, method, 'Initializing all cloudant triggers from database.');

        triggerDB.list({include_docs: true}, function(err, body) {
            if(!err) {
                body.rows.forEach(function(trigger) {
                    var cloudantTrigger = that.initTrigger(trigger.doc, trigger.doc.id);
                    // check here for triggers left if none left end here, and dont create
                    //if (cloudantTrigger.triggersLeft > 0) {
                      that.createTrigger(cloudantTrigger, 10);
                    //} else {
                    //  logger.info(tid, method, 'found a trigger with no triggers left to fire off.');
                    //}
                });
            } else {
                logger.error(tid, method, 'could not get latest state from database');
            }
        });

    };

    // Delete a trigger: stop listening for changes and remove it.
    this.deleteTrigger = function (id) {
        var method = 'deleteTrigger';
        var trigger = that.triggers[id];
        if (trigger) {
            logger.info(tid, method, 'Stopped cloudant trigger',
                id, 'listening for changes in database', trigger.dbname);
            trigger.feed.stop();
            delete that.triggers[id];
        } else {
            logger.info(tid, method, 'trigger', id, 'could not be found in the trigger list.');
            return false;
        }
    };

    this.addTriggerToDB = function (trigger, res) {

        var method = 'addTriggerToDB';
        triggerDB.insert(_.omit(trigger, 'feed'), trigger.id, function(err, body) {
            if(!err) {
                logger.info(tid, method, 'trigger', trigger.id, 'was inserted into db.');
                res.status(200).json(_.omit(trigger, 'feed'));
            } else {
                that.deleteTrigger(trigger.id);
                res.status(err.statusCode).json({error: 'Cloudant trigger cannot be created.'});
            }
        });

    };

    this.deleteTriggerFromDB = function (id, res) {

        var method = 'deleteTriggerFromDB';
        triggerDB.get(id, function(err, body) {
            if(!err) {
                triggerDB.destroy(body._id, body._rev, function(err, body) {
                    if(err) {
                        logger.error(tid, method, 'there was an error while deleting', id, 'from database');
                        if (res) {
                            res.status(err.statusCode).json({ error: 'Cloudant data trigger ' + id  + 'cannot be deleted.' } );
                        }
                    } else {
                        that.deleteTrigger(id);
                        logger.info(tid, method, 'cloudant trigger', id, ' is successfully deleted');
                        if (res) {
                            res.send('Deleted cloudant trigger ' + id);
                        }
                    }
                });
            } else {
                if (err.statusCode === 404) {
                    logger.info(tid, method, 'there was no trigger with id', id, 'in database.', err.error);
                    if (res) {
                        res.status(200).json({ message: 'there was no trigger with id ' + id + ' in database.' } );
                        res.end();
                    }
                } else {
                    logger.error(tid, method, 'there was an error while getting', id, 'from database', err);
                    if (res) {
                        res.status(err.statusCode).json({ error: 'Cloudant data trigger ' + id  + ' cannot be deleted.' } );
                    }
                }
            }
        });

    };

    this.invokeWhiskAction = function (id, change) {
        var method = 'invokeWhiskAction';

        var dataTrigger = that.triggers[id];
        var apikey = dataTrigger.apikey;
        var triggerName = dataTrigger.callback.action.name;
        var triggerObj = that.parseQName(triggerName);
        logger.info(tid, method, 'invokeWhiskAction: change =', change);
        var form = change.hasOwnProperty('doc') ? change.doc : change;
        logger.info(tid, method, 'invokeWhiskAction: form =', form);
        logger.info(tid, method, 'for trigger', id, 'invoking action', triggerName, 'with db update', JSON.stringify(form));

        var host = 'https://'+routerHost+':'+443;
        var uri = host+'/api/v1/namespaces/' + triggerObj.namespace +'/triggers/'+triggerObj.name;
        var auth = apikey.split(':');
        logger.info(tid, method, uri, auth, form);

        dataTrigger.triggersLeft--;

        request({
            method: 'post',
            uri: uri,
            auth: {
                user: auth[0],
                pass: auth[1]
            },
            json: form
        }, function(error, response, body) {
            if(dataTrigger) {
                logger.info(tid, method, 'done http request, STATUS', response ? response.statusCode : response);
                logger.info(tid, method, 'done http request, body', body);
                if(error || response.statusCode >= 400) {
                    dataTrigger.retriesLeft--;
                    dataTrigger.triggersLeft++; // setting the counter back to where it used to be
                    logger.error(tid, method, 'there was an error invoking', id, response ? response.statusCode : response, error, body);
                } else {
                    dataTrigger.retriesLeft = that.retriesBeforeDelete; // reset retry counter
                    logger.info(tid, method, 'fired', id, 'with body', body, dataTrigger.triggersLeft, 'triggers left');
                }

                if(dataTrigger.triggersLeft === 0 || dataTrigger.retriesLeft === 0) {
                    if(dataTrigger.triggersLeft === 0)
                        logger.info(tid, 'onTick', 'no more triggers left, deleting');
                    if(dataTrigger.retriesLeft === 0)
                        logger.info(tid, 'onTick', 'too many retries, deleting');

                    that.deleteTriggerFromDB(dataTrigger.id);
                }
            } else {
                logger.info(tid, method, 'trigger', id, 'was deleted between invocations');
            }
        });
    };

    this.parseQName = function (qname) {
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
    };

    this.authorize = function(req, res, next) {
        next();
    }

};
