watchme
=======

WatchMe is a service which allows a user to publish a live animation of their computing activities for the last two hours.

It works by uploading a bi-minutely (chosen to avoid rate limiting of imgur API) screenshot to imgur, the last 60 of which are compiled into a Javascript-driven image animation that can be posted anywhere.

The project is in two parts:

1. A small Java client program which takes the screenshots and uploads them to both imgur and our backend
2. A thin backend API created in NodeJS which maintains some metadata about the imgur images, as well as handles deleting any images beyond the most recent 60. 