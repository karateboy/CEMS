/**
 * 
 */
angular
		.module(
				'alarmView',
				[ 'ui.bootstrap.datetimepicker', 'ui.dateTimeInput',
						'smart-table' ])
		.factory('MonitorTypeService', [ '$resource', function($resource) {
			return $resource('/MonitorType/:id', {
				id : '@_id'
			});
		} ])
		.factory('MonitorService', [ '$resource', function($resource) {
			return $resource('/Monitor/:id', {
				id : '@_id'
			});
		} ])
		.factory('IndParkService', [ '$resource', function($resource) {
			return $resource('/IndPark/:id', {
				id : '@_id'
			});
		} ])
		.factory('ReportUnitService', [ '$resource', function($resource) {
			return $resource('/ReportUnit/:id', {
				id : '@_id'
			});
		} ])
		.controller(
				'alarmCtrl',
				[
						'MonitorTypeService',
						'MonitorService',
						'IndParkService',
						'ReportUnitService',
						'$scope',
						'$http',
						function($monitorType, $monitor, $indPark, $reportUnit,
								$scope, $http) {
							var self = this;
							self.monitorTypeList = $monitorType.query();
							self.monitorList = $monitor.query();
							self.indParkList = $indPark.query();
							self.reportUnitList = $reportUnit.query();

							self.dateRangeStart = moment(23, "HH").subtract(2,
									'days');
							self.dateRangeEnd = moment(23, "HH");

							self.beforeRenderStartDate = function($view,
									$dates, $leftDate, $upDate, $rightDate) {
								if (self.dateRangeEnd) {
									var activeDate = moment(self.dateRangeEnd);
									for (var i = 0; i < $dates.length; i++) {
										if ($dates[i].localDateValue() >= activeDate
												.valueOf())
											$dates[i].selectable = false;
									}
								}
							}

							self.beforeRenderEndDate = function($view, $dates,
									$leftDate, $upDate, $rightDate) {
								if ($scope.dateRangeStart) {
									var activeDate = moment(self.dateRangeStart)
											.subtract(1, $view)
											.add(1, 'minute');
									for (var i = 0; i < $dates.length; i++) {
										if ($dates[i].localDateValue() <= activeDate
												.valueOf()) {
											$dates[i].selectable = false;
										}
									}
								}
							}

							function getSelectedArrayFunction(obj) {
								return function() {
									var ret = [];
									for ( var prop in obj) {
										if (obj[prop])
											ret.push(prop);
									}
									return ret;
								}
							}

							$scope.checkedIndPark = {};
							self.selectedIndPark = getSelectedArrayFunction($scope.checkedIndPark);
							$scope.checkedMonitorId = {};
							self.selectedMonitorId = getSelectedArrayFunction($scope.checkedMonitorId);
							$scope.checkedMtId = {};
							self.selectedMtId = getSelectedArrayFunction($scope.checkedMtId);

							self.indParkMonitor = function(m) {
								return (self.selectedIndPark().indexOf(
										m.indParkName) != -1)
							}

							self.selectAllMonitor = function(){
								self.monitorList.forEach(function(element, index, array){
									if($scope.checkedIndPark[element.indParkName])
										$scope.checkedMonitorId[element._id]=true;
								});
							}
							
							self.selectAllMonitorType = function(){
								self.monitorTypeList.forEach(function(element, index, array){
									$scope.checkedMtId[element._id]=true;
								});
							}
							self.displayResult = false;
							self.query = function() {
								var url = "/AlarmReport/"
										+ encodeURIComponent(self.selectedMonitorId().join(':'))
										+ "/"
										+ encodeURIComponent(self.selectedMtId().join(':'))
										+ "/" + self.dateRangeStart.valueOf()
										+ "/" + self.dateRangeEnd.valueOf();

								$http.get(url).then(function(result) {
									self.displayResult = true;
									$('#reportDiv').html(result.data);
								}, function(error) {
								});
							}

						} ]);
