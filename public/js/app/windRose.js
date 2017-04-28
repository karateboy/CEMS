/**
 * 
 */
angular
		.module(
				'windRoseView',
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
				'windRoseCtrl',
				[
						'MonitorTypeService',
						'MonitorService',
						'IndParkService',
						'ReportUnitService',
						'$scope',
						'$http',
						function($monitorType, $monitor, $indPark, $reportUnit, $scope, $http) {
							var self = this;
							self.monitorTypeList = $monitorType.query();
							self.monitorList = $monitor.query();
							self.indParkList = $indPark.query();
							self.reportUnitList = $reportUnit.query();
							self.nWayList = [8, 16, 32];
							self.nWay = 8;
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
							
							$scope.selectedIndPark;
							$scope.selectedMonitorId;
							$scope.selectedMtId;
							$scope.nWay;
							
							self.indParkMonitor = function(m) {
								return ($scope.selectedIndPark == m.indParkName)
							}

							self.displayResult = false;
							self.query = function() {
								var base_url = encodeURIComponent($scope.selectedMonitorId) + 
									"/" + encodeURIComponent($scope.selectedMtId) + 
									"/" + $scope.nWay +  
									"/" + self.dateRangeStart.valueOf() + 
									"/" + self.dateRangeEnd.valueOf();
								
								var url = "/JSON/WindRose/" + base_url; 
									+  

								$http.get(url)
									.then(
										function(response) {
											self.displayResult = true;
											var result = response.data;
											/*result.colors=[
															'#7CB5EC','#434348','#90ED7D','#F7A35C','#8085E9','#F15C80',
															'#E4D354','#2B908F','#FB9FA8','#91E8E1','#7CB5EC','#80C535','#969696'];
											*/
											result.pane={
													size: '90%'
											};			
											result.legend={
													align: 'right',
													verticalAlign: 'top',
													y: 100,
													layout: 'vertical'
											};
											result.yAxis={
													min: 0,
													endOnTick: false,
													showLastLabel: true,
													title: {
														text: '頻率 (%)'
													},
													labels: {
														formatter: function () {
															return this.value + '%';
														}
													},
													reversedStacks: false
											};

											result.tooltip={
												valueDecimals: 2,
												valueSuffix: '%'
											};

											result.plotOptions={
													series: {
														stacking: 'normal',
														shadow: false,
														groupPadding: 0,
														pointPlacement: 'on'
													}
											};
											result.credits={
													enabled:false,
													href:'http://www.wecc.com.tw/'
											};
											result.title.x = -70;
											$("#downloadExcel").prop("href", "/Excel" + url);
											$('#reportDiv').highcharts(result);																																
										}, function(error) {
											alert("無資料!");
										});
							}													
						} ]);
