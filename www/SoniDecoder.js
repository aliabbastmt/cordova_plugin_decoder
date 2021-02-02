var exec = require('cordova/exec');

exports.decode = function (arg0, success, error) {
    exec(success, error, 'SoniDecoder', 'decode', [arg0]);
};

exports.check_microphone_permission = function (success, error) {
    exec(success, error, 'SoniDecoder', 'check_microphone_permission');
};

exports.check_special_permission = function (success, error) {
    exec(success, error, 'SoniDecoder', 'check_special_permission');
};