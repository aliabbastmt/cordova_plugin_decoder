var exec = require('cordova/exec');

exports.decode = function (arg0, success, error) {
    exec(success, error, 'SoniDecoder', 'decode', [arg0]);
};