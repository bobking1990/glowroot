/*
 * Copyright 2012-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* global glowroot, angular, $ */

glowroot.controller('TracesCtrl', [
  '$scope',
  '$location',
  '$http',
  '$q',
  'locationChanges',
  'charts',
  'httpErrors',
  'traceModal',
  'queryStrings',
  'traceKind',
  function ($scope, $location, $http, $q, locationChanges, charts, httpErrors, traceModal, queryStrings, traceKind) {

    $scope.$parent.activeTabItem = 'traces';

    if ($scope.hideMainContent()) {
      return;
    }

    var plot;

    var $chart = $('#chart');

    var appliedFilter;

    var defaultFilterLimit = 500;

    var highlightedTraceId;

    $scope.showChartSpinner = 0;
    $scope.showErrorMessageFilter = traceKind === 'error';

    $scope.filterLimitOptions = [
      {text: '100', value: 100},
      {text: '200', value: 200},
      {text: '500', value: 500},
      {text: '1,000', value: 1000},
      {text: '2,000', value: 2000},
      {text: '5,000', value: 5000}
    ];

    $scope.$watchGroup(['range.chartFrom', 'range.chartTo', 'range.chartRefresh'], function () {
      appliedFilter.from = $scope.traceChartFrom || $scope.range.chartFrom;
      appliedFilter.to = $scope.traceChartTo || $scope.range.chartTo;
      updateLocation();
      if ($scope.suppressChartRefresh) {
        $scope.suppressChartRefresh = false;
        return;
      }
      refreshChart();
    });

    function refreshChart(deferred) {
      if ((!$scope.agentRollup && !$scope.layout.fat) || !$scope.transactionType) {
        return;
      }
      var from = appliedFilter.from;
      var to = appliedFilter.to;
      var limit = appliedFilter.limit;
      if (appliedFilter.durationMillisLow) {
        appliedFilter.durationMillisLow = Number(appliedFilter.durationMillisLow);
      } else if (appliedFilter.durationMillisLow === '') {
        appliedFilter.durationMillisLow = 0;
      }
      if (appliedFilter.durationMillisHigh) {
        appliedFilter.durationMillisHigh = Number(appliedFilter.durationMillisHigh);
      } else if (appliedFilter.durationMillisHigh === '') {
        appliedFilter.durationMillisHigh = undefined;
      }
      var durationMillisLow = appliedFilter.durationMillisLow;
      var durationMillisHigh = appliedFilter.durationMillisHigh;
      var query = angular.copy(appliedFilter);
      if ($scope.transactionName) {
        query.transactionName = $scope.transactionName;
      }
      query.agentRollup = $scope.agentRollup;
      var showChartSpinner = !$scope.suppressChartSpinner;
      if (showChartSpinner) {
        $scope.showChartSpinner++;
      }
      $scope.suppressChartSpinner = false;
      $http.get('backend/' + traceKind + '/points' + queryStrings.encodeObject(query))
          .success(function (data) {
            function tryHighlight(dataPoints, dataSeries) {
              var i;
              for (i = 0; i < dataPoints.length; i++) {
                var activePoint = dataPoints[i];
                if (activePoint[3] === highlightedTraceId) {
                  plot.highlight(dataSeries, activePoint.slice(0, 2));
                  return true;
                }
              }
              return false;
            }

            function highlight() {
              if (tryHighlight(data.normalPoints, plot.getData()[0])) {
                return;
              }
              if (tryHighlight(data.errorPoints, plot.getData()[1])) {
                return;
              }
              tryHighlight(data.activePoints, plot.getData()[2]);
            }

            // clear http error, especially useful for auto refresh on live data to clear a sporadic error from earlier
            $scope.httpError = undefined;
            if (showChartSpinner) {
              $scope.showChartSpinner--;
            }
            if ($scope.showChartSpinner) {
              // ignore this response, another response has been stacked
              return;
            }
            var traceCount = data.normalPoints.length + data.errorPoints.length + data.activePoints.length;
            $scope.chartNoData = traceCount === 0;
            $scope.showExpiredMessage = data.expired;
            $scope.chartLimitExceeded = data.limitExceeded;
            $scope.chartLimit = limit;
            $scope.traceAttributeNames = data.traceAttributeNames;
            // user clicked on Refresh button, need to reset axes
            plot.getAxes().xaxis.options.min = from;
            plot.getAxes().xaxis.options.max = to;
            plot.getAxes().yaxis.options.min = durationMillisLow;
            plot.getAxes().yaxis.options.realMax = durationMillisHigh;
            plot.setData([data.normalPoints, data.errorPoints, data.activePoints]);
            // setupGrid is needed in case yaxis.max === undefined
            if (highlightedTraceId) {
              plot.unhighlight();
            }
            plot.setupGrid();
            plot.draw();
            if (highlightedTraceId) {
              highlight();
            }
            broadcastTraceTabCount();
            if (deferred) {
              deferred.resolve('Success');
            }
          })
          .error(function (data, status) {
            if (showChartSpinner) {
              $scope.showChartSpinner--;
            }
            httpErrors.handler($scope, deferred)(data, status);
          });
    }

    function broadcastTraceTabCount() {
      var data = plot.getData();
      var traceCount = data[0].data.length + data[1].data.length + data[2].data.length;
      // parent scope can be null if user has moved on to another controller by the time http get returns
      if ($scope.$parent) {
        if ($scope.chartLimitExceeded) {
          $scope.$parent.$broadcast('updateTraceTabCount', undefined);
        } else {
          $scope.$parent.$broadcast('updateTraceTabCount', traceCount);
        }
      }
    }

    $scope.refresh = function () {
      $scope.applyLast();
      angular.extend(appliedFilter, $scope.filter);
      $scope.range.chartRefresh++;
    };

    $scope.clearCriteria = function () {
      $scope.filter.durationMillisLow = 0;
      $scope.filter.durationMillisHigh = undefined;
      $scope.filterDurationComparator = 'greater';
      $scope.filter.headlineComparator = 'begins';
      $scope.filter.headline = '';
      $scope.filter.errorMessageComparator = 'begins';
      $scope.filter.errorMessage = '';
      $scope.filter.userComparator = 'begins';
      $scope.filter.user = '';
      $scope.filter.attributeName = '';
      $scope.filter.attributeValueComparator = 'begins';
      $scope.filter.attributeValue = '';
      $scope.filter.limit = defaultFilterLimit;
      $scope.refresh();
    };

    function postUpdateRange(range) {
      if (range.to - range.from >= 300000) {
        // lock to overall chartFrom/chartTo (just updated above in updateRange) if range > 5 minutes
        range.from = $scope.range.chartFrom;
        range.to = $scope.range.chartTo;
        delete $scope.traceChartFrom;
        delete $scope.traceChartTo;
      } else {
        // use chartFrom/chartTo (just updated above in updateRange) as bounds for from/to:
        range.from = Math.max(range.from, $scope.range.chartFrom);
        range.to = Math.min(range.to, $scope.range.chartTo);
        $scope.traceChartFrom = range.from;
        $scope.traceChartTo = range.to;
        // last doesn't make sense with trace-chart-from/trace-chart-to (what to do on browser refresh at later time?)
        delete $scope.range.last;
      }
    }

    $chart.bind('plotzoom', function (event, plot, args) {
      var zoomingOut = args.amount && args.amount < 1;
      $scope.$apply(function () {
        var range = {
          from: plot.getAxes().xaxis.options.min,
          to: plot.getAxes().xaxis.options.max
        };
        charts.updateRange($scope, range.from, range.to, zoomingOut);
        postUpdateRange(range);
        if (zoomingOut) {
          // scroll zooming out, reset duration limits
          updateFilter(range.from, range.to, 0, undefined);
        } else {
          // only update from/to
          appliedFilter.from = range.from;
          appliedFilter.to = range.to;
        }
        if (zoomingOut || $scope.chartLimitExceeded) {
          updateLocation();
        } else {
          // no need to fetch new data
          $scope.suppressChartRefresh = true;
          var data = getFilteredData();
          plot.setData(data);
          plot.setupGrid();
          plot.draw();
          broadcastTraceTabCount();
          updateLocation();
        }
      });
    });

    $chart.bind('plotselected', function (event, ranges) {
      $scope.$apply(function () {
        plot.clearSelection();
        var range = {
          from: ranges.xaxis.from,
          to: ranges.xaxis.to
        };
        charts.updateRange($scope, range.from, range.to, false, true, true);
        postUpdateRange(range);
        updateFilter(range.from, range.to, ranges.yaxis.from, ranges.yaxis.to);
        if ($scope.chartLimitExceeded) {
          updateLocation();
        } else {
          // no need to fetch new data
          $scope.suppressChartRefresh = true;
          plot.getAxes().xaxis.options.min = range.from;
          plot.getAxes().xaxis.options.max = range.to;
          plot.getAxes().yaxis.options.min = ranges.yaxis.from;
          plot.getAxes().yaxis.options.realMax = ranges.yaxis.to;
          plot.setData(getFilteredData());
          // setupGrid needs to be after setData
          plot.setupGrid();
          plot.draw();
          broadcastTraceTabCount();
          updateLocation();
        }
      });
    });

    $scope.zoomOut = function () {
      var currMin = $scope.range.chartFrom;
      var currMax = $scope.range.chartTo;
      var currRange = currMax - currMin;
      var range = {
        from: currMin - currRange / 2,
        to: currMax + currRange / 2
      };
      charts.updateRange($scope, range.from, range.to, true);
      postUpdateRange(range);
      if ($scope.traceChartFrom && $scope.traceChartTo) {
        // use updated $scope.range.chartFrom/To as bounds for from/to:
        $scope.traceChartFrom = Math.max(range.from, $scope.range.chartFrom);
        $scope.traceChartTo = Math.min(range.to, $scope.range.chartTo);
      }
    };

    function updateFilter(from, to, durationMillisLow, durationMillisHigh) {
      appliedFilter.from = from;
      appliedFilter.to = to;
      // set both appliedFilter and $scope.filter durationMillisLow/durationMillisHigh
      appliedFilter.durationMillisLow = $scope.filter.durationMillisLow = durationMillisLow;
      appliedFilter.durationMillisHigh = $scope.filter.durationMillisHigh = durationMillisHigh;
      if (durationMillisHigh && durationMillisLow !== 0) {
        $scope.filterDurationComparator = 'between';
      } else if (durationMillisHigh) {
        $scope.filterDurationComparator = 'less';
      } else {
        $scope.filterDurationComparator = 'greater';
      }
    }

    function getFilteredData() {
      var from = plot.getAxes().xaxis.options.min;
      var to = plot.getAxes().xaxis.options.max;
      var durationMillisLow = plot.getAxes().yaxis.options.min;
      var durationMillisHigh = plot.getAxes().yaxis.options.realMax || Number.MAX_VALUE;
      var data = [];
      var i, j;
      var nodata = true;
      for (i = 0; i < plot.getData().length; i++) {
        data.push([]);
        var points = plot.getData()[i].data;
        for (j = 0; j < points.length; j++) {
          var point = points[j];
          if (point[0] >= from && point[0] <= to && point[1] >= durationMillisLow
              && point[1] <= durationMillisHigh) {
            data[i].push(point);
            nodata = false;
          }
        }
      }
      $scope.chartNoData = nodata;
      return data;
    }

    $chart.bind('plotclick', function (event, pos, item, originalEvent) {
      if (item) {
        plot.unhighlight();
        plot.highlight(item.series, item.datapoint);
        var agentId = plot.getData()[item.seriesIndex].data[item.dataIndex][2];
        var traceId = plot.getData()[item.seriesIndex].data[item.dataIndex][3];
        var checkLiveTraces = item.seriesIndex === 2;
        if (originalEvent.ctrlKey) {
          var url = $location.url();
          if (url.indexOf('?') === -1) {
            url += '?';
          } else {
            url += '&';
          }
          if (agentId) {
            url += 'modal-agent-id=' + agentId + '&';
          }
          url += 'modal-trace-id=' + traceId;
          if (checkLiveTraces) {
            url += '&modal-check-live-traces=true';
          }
          window.open(url);
        } else {
          $scope.$apply(function () {
            if (agentId) {
              $location.search('modal-agent-id', agentId);
            }
            $location.search('modal-trace-id', traceId);
            if (checkLiveTraces) {
              $location.search('modal-check-live-traces', 'true');
            }
          });
        }
        highlightedTraceId = item.seriesIndex === 2 ? traceId : null;
      }
    });

    $scope.filterDurationComparatorOptions = [
      {
        display: 'Greater than',
        value: 'greater'
      },
      {
        display: 'Less than',
        value: 'less'
      },
      {
        display: 'Between',
        value: 'between'
      }
    ];

    $scope.filterTextComparatorOptions = [
      {
        display: 'Begins with',
        value: 'begins'
      },
      {
        display: 'Equals',
        value: 'equals'
      },
      {
        display: 'Ends with',
        value: 'ends'
      },
      {
        display: 'Contains',
        value: 'contains'
      },
      {
        display: 'Does not contain',
        value: 'not-contains'
      }
    ];

    locationChanges.on($scope, function () {
      var priorAppliedFilter = appliedFilter;
      appliedFilter = {};
      appliedFilter.transactionType = $scope.transactionType;
      $scope.traceChartFrom = Number($location.search()['trace-chart-from']);
      $scope.traceChartTo = Number($location.search()['trace-chart-to']);
      appliedFilter.from = $scope.traceChartFrom || $scope.range.chartFrom;
      appliedFilter.to = $scope.traceChartTo || $scope.range.chartTo;
      appliedFilter.durationMillisLow = Number($location.search()['duration-millis-low']) || 0;
      appliedFilter.durationMillisHigh = Number($location.search()['duration-millis-high']) || undefined;
      appliedFilter.headlineComparator = $location.search()['headline-comparator'] || 'begins';
      appliedFilter.headline = $location.search().headline || '';
      appliedFilter.errorMessageComparator = $location.search()['error-message-comparator'] || 'begins';
      appliedFilter.errorMessage = $location.search()['error-message'] || '';
      appliedFilter.userComparator = $location.search()['user-comparator'] || 'begins';
      appliedFilter.user = $location.search().user || '';
      appliedFilter.attributeName = $location.search()['custom-attribute-name'] || '';
      appliedFilter.attributeValueComparator = $location.search()['custom-attribute-value-comparator'] || 'begins';
      appliedFilter.attributeValue = $location.search()['custom-attribute-value'] || '';
      appliedFilter.limit = Number($location.search().limit) || defaultFilterLimit;

      if (priorAppliedFilter !== undefined && !angular.equals(appliedFilter, priorAppliedFilter)) {
        // e.g. back or forward button was used to navigate
        $scope.range.chartRefresh++;
      }

      $scope.filter = angular.copy(appliedFilter);
      // need to remove from and to so they aren't copied back during angular.extend(appliedFilter, $scope.filter)
      delete $scope.filter.from;
      delete $scope.filter.to;

      if (appliedFilter.durationMillisLow !== 0 && appliedFilter.durationMillisHigh) {
        $scope.filterDurationComparator = 'between';
      } else if (appliedFilter.durationMillisHigh) {
        $scope.filterDurationComparator = 'less';
      } else {
        $scope.filterDurationComparator = 'greater';
      }

      var modalAgentId = $location.search()['modal-agent-id'] || '';
      var modalTraceId = $location.search()['modal-trace-id'];
      var modalCheckLiveTraces = $location.search()['modal-check-live-traces'];
      if (modalTraceId) {
        highlightedTraceId = modalTraceId;
        $('#traceModal').data('location-query', ['modal-agent-id', 'modal-trace-id', 'modal-check-live-traces']);
        traceModal.displayModal(modalAgentId, modalTraceId, modalCheckLiveTraces);
      } else {
        $('#traceModal').modal('hide');
      }
    });

    $scope.$watch('filterDurationComparator', function (value) {
      if (value === 'greater') {
        $scope.filter.durationMillisHigh = undefined;
      } else if (value === 'less') {
        $scope.filter.durationMillisLow = 0;
      }
    });

    function updateLocation() {
      var query = $scope.buildQueryObject({});
      if ($scope.traceChartFrom) {
        query['trace-chart-from'] = $scope.traceChartFrom;
      }
      if ($scope.traceChartTo) {
        query['trace-chart-to'] = $scope.traceChartTo;
      }
      if (Number(appliedFilter.durationMillisLow)) {
        query['duration-millis-low'] = appliedFilter.durationMillisLow;
      }
      if (Number(appliedFilter.durationMillisHigh)) {
        query['duration-millis-high'] = appliedFilter.durationMillisHigh;
      }
      if (appliedFilter.headline) {
        query['headline-comparator'] = appliedFilter.headlineComparator;
        query.headline = appliedFilter.headline;
      }
      if (appliedFilter.errorMessage) {
        query['error-message-comparator'] = appliedFilter.errorMessageComparator;
        query['error-message'] = appliedFilter.errorMessage;
      }
      if (appliedFilter.user) {
        query['user-comparator'] = appliedFilter.userComparator;
        query.user = appliedFilter.user;
      }
      if (appliedFilter.attributeName) {
        query['custom-attribute-name'] = appliedFilter.attributeName;
      }
      if (appliedFilter.attributeValue) {
        query['custom-attribute-value-comparator'] = appliedFilter.attributeValueComparator;
        query['custom-attribute-value'] = appliedFilter.attributeValue;
      }
      if (Number(appliedFilter.limit) !== defaultFilterLimit) {
        query.limit = appliedFilter.limit;
      }
      // preserve modal-*, otherwise refresh on modal trace does not work
      query['modal-agent-id'] = $location.search()['modal-agent-id'];
      query['modal-trace-id'] = $location.search()['modal-trace-id'];
      query['modal-check-live-traces'] = $location.search()['modal-check-live-traces'];
      $location.search(query);
    }

    (function () {
      var options = {
        legend: {
          show: false
        },
        series: {
          points: {
            show: true
          }
        },
        grid: {
          hoverable: true,
          clickable: true,
          // min border margin should match aggregate chart so they are positioned the same from the top of page
          // without specifying min border margin, the point radius is used
          minBorderMargin: 0,
          borderColor: '#7d7358',
          borderWidth: 1
        },
        xaxis: {
          mode: 'time',
          timezone: 'browser',
          twelveHourClock: true,
          ticks: 5,
          // xaxis is in milliseconds, so grid lock to 1 second
          borderGridLock: 1000,
          min: appliedFilter.from,
          max: appliedFilter.to,
          reserveSpace: false
        },
        yaxis: {
          ticks: 10,
          zoomRange: false,
          borderGridLock: 1,
          min: 0,
          // 10 second yaxis max just for initial empty chart rendering
          max: 10,
          label: 'milliseconds'
        },
        zoom: {
          interactive: true,
          ctrlKey: true,
          amount: 2,
          skipDraw: true
        },
        colors: [
          $('#offscreenNormalColor').css('border-top-color'),
          $('#offscreenErrorColor').css('border-top-color'),
          $('#offscreenActiveColor').css('border-top-color')
        ],
        selection: {
          mode: 'xy'
        }
      };
      // render chart with no data points
      plot = $.plot($chart, [[]], options);
    })();

    plot.getAxes().yaxis.options.max = undefined;
    charts.initResize(plot, $scope);
    charts.startAutoRefresh($scope, 60000);
  }
]);
