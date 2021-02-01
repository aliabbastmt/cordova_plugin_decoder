var exec = require('cordova/exec');

exports.decode = function (arg0, success, error) {
    exec(success, error, 'SoniDecoder', 'decode', [arg0]);
};

exports.decode = function (arg0, success, error) {
    exec(success, error, 'SoniDecoder', 'check_microphone_permission', [arg0]);
};


exports.decode = function (arg0, success, error) {
    exec(success, error, 'SoniDecoder', 'check_special_permission', [arg0]);
};
