/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Karma configuration
// http://karma-runner.github.io/0.12/config/configuration-file.html
// Generated on 2014-08-29 using
// generator-karma 0.8.3

var webpackConfig = require('./webpack.config');

module.exports = function(config) {
  'use strict';

  config.set({
    // enable / disable watching file and executing tests whenever any file changes
    autoWatch: true,

    // base path, that will be used to resolve files and exclude
    basePath: './',

    // testing framework to use (jasmine/mocha/qunit/...)
    frameworks: ['jasmine'],

    // list of files / patterns to load in the browser
    files: [
      // for polyfill
      'node_modules/babel-polyfill/dist/polyfill.js',

      // bower:js
      'bower_components/ace-builds/src-noconflict/ace.js',
      'bower_components/ace-builds/src-noconflict/mode-scala.js',
      'bower_components/ace-builds/src-noconflict/mode-python.js',
      'bower_components/ace-builds/src-noconflict/mode-sql.js',
      'bower_components/ace-builds/src-noconflict/mode-markdown.js',
      'bower_components/ace-builds/src-noconflict/mode-pig.js',
      'bower_components/ace-builds/src-noconflict/mode-sh.js',
      'bower_components/ace-builds/src-noconflict/mode-r.js',
      'bower_components/ace-builds/src-noconflict/keybinding-emacs.js',
      'bower_components/ace-builds/src-noconflict/ext-language_tools.js',
      'bower_components/ace-builds/src-noconflict/theme-chrome.js',
      // endbower

      'src/index.js',
      { pattern: 'src/**/*.test.js', watched: false },
    ],

    // list of files / patterns to exclude
    exclude: [
      '.tmp/app/visualization/builtins/*.js'
    ],

    // web server port
    port: 9002,

    // Start these browsers, currently available:
    // - Chrome
    // - ChromeCanary
    // - Firefox
    // - Opera
    // - Safari (only Mac)
    // - PhantomJS
    // - IE (only Windows)
    browsers: [ 'PhantomJS' ],

    reporters: ['spec', 'coverage'],

    webpack: webpackConfig,
    webpackMiddleware: {
      stats: 'errors-only'
    },

    preprocessors: {
      'src/**/*.js': ['webpack', 'sourcemap'],
    },

    coverageReporter: {
      dir: 'reports/coverage',
      reporters: [
        { type: 'html' },
        { type: 'text-summary' },
      ]
    },

    // Continuous Integration mode
    // if true, it capture browsers, run tests and exit
    singleRun: true,

    colors: true,

    // level of logging
    // possible values: LOG_DISABLE || LOG_ERROR || LOG_WARN || LOG_INFO || LOG_DEBUG
    logLevel: config.LOG_INFO,

    // Uncomment the following lines if you are using grunt's server to run the tests
    // proxies: {
    //   '/': 'http://localhost:9000/'
    // },
    // URL root prevent conflicts with the site root
    // urlRoot: '_karma_'
  });
};
