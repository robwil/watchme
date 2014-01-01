'use strict';

/*
 * Express Dependencies
 */
var express = require('express');
var app = express();
var port = 3000;
var superagent = require('superagent');
var fs = require('fs'),
	nconf = require('nconf');
var redis = require("redis"),
    client = redis.createClient();

//
// Setup nconf to use (in-order):
//   1. Command-line arguments
//   2. Environment variables
//   3. A file located at 'config.json'
//
nconf.argv().env().file({ file: 'config.json' });
var IMGUR_CLIENT_ID = nconf.get('IMGUR_CLIENT_ID');
console.log("imgur.com client id: " + IMGUR_CLIENT_ID);

// Express general setup
app.use(express.logger());
app.use(express.compress());
app.use(express.bodyParser());

// View setup
app.engine('.html', require('ejs').__express);
app.set('views', __dirname + '/views');

/*
 * Routes
 */
app.get('/photos', function(request, response) {
	// Fetch the last 60 images from Redis store
	client.lrange("images", 0, 59, function(err, replies) {
		var images = [];
		// Extract imgur links from DB format ('imgurLink||deleteHash')
		// While reversing (to put in chronological order instead of reverse chron.)
		for (var i = replies.length - 1; i >= 0; i--) {
			images.push(replies[i].split("||")[0]);
		}
		// Render the photos with a JS animation
		response.render("photos.html", {
			locals: {
				images: images
			}
		});
	});
});
app.post('/photos', function(request, response) {
	client.lpush("images", request.body.imgurLink + "||" + request.body.deleteHash, function(err, items) {
		// If there are more than 60 items, delete the oldest one.
		// (We are hoping there are only 1 extra.. SHOULD be true if nothing weird happens.)
		console.log("Items = " + items);
		if (items > 60) {
			// Delete it from the Redis list, while also retrieving it.
			client.rpop("images", function(err, reply) {
				console.log("RPOP Reply = " + reply);
				// Actually delete the image from imgur too.
				var deleteHash = reply.split("||")[1];
				var agent = superagent.agent();
				agent.del("https://api.imgur.com/3/image/" + deleteHash).set("Authorization", "Client-ID " + IMGUR_CLIENT_ID).end(function(imgurResponse) {
					console.log("IMGUR response = " + imgurResponse);
					response.statusCode = (imgurResponse.body.success ? 200 : 500);
					response.end();
				});
			});
		} else {
			response.statusCode = 200;
			response.end();
		}
	});
});

app.use(function(err, req, res, next){
	console.error(err.stack);
	res.send(500, 'Something broke!');
});

/*
 * Start it up
 */
app.listen(process.env.PORT || port);
console.log('Express started on port ' + port);