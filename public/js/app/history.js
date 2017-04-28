/**
 * 
 */
angular
		.module(
				'historyView',
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
		.controller(
				'historyCtrl',
				[
						'MonitorTypeService',
						'MonitorService',
						'IndParkService',
						'$scope',
						'$http',
						function($monitorType, $monitor, $indPark, $scope, $http) {
							var self = this;
							self.monitorTypeList = $monitorType.query();
							self.monitorList = $monitor.query();
							self.indParkList = $indPark.query();
							self.indParkMonitor = function(m) {
								return (m.indParkName == $scope.selectedIndPark)
							}

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

							$scope.selectedMonitorId;
							$scope.checkedMtId={};
							$scope.selectedMtId = function(){
								var ret = [];
								for (var prop in $scope.checkedMtId) {
									if($scope.checkedMtId[prop])
										ret.push(prop);
								}
								return ret;
							}
							
							$scope.selectedIndPark="";

							self.displayResult = false;
							self.query = function() {								
								var url = "/HistoryReport/" + encodeURIComponent($scope.selectedMonitorId) 
										+ "/" + encodeURIComponent($scope.selectedMtId().join(':'))
										+ "/min"
										+ "/" + self.dateRangeStart.valueOf()
										+ "/" + self.dateRangeEnd.valueOf();

								$http
										.get(url)
										.then(
												function(result) {
													self.displayResult = true;
													$('#reportDiv').html(result.data);
												}, function(error) {
												});
							}
							
							
						} ]);
