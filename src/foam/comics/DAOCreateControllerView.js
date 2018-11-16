/**
 * @license
 * Copyright 2017 The FOAM Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

foam.CLASS({
  package: 'foam.comics',
  name: 'DAOCreateControllerView',
  extends: 'foam.u2.View',

  requires: [
    'foam.comics.DAOCreateController'
  ],

  imports: [
    'dao',
    'stack'
  ],

  exports: [
    'data'
  ],

  css: `
    ^ .net-nanopay-ui-ActionView {
      float: right;
      width: 128px;
      height: 40px;
      background: #0030f9;;
      color: white;
      border-radius: 4px;
      box-shadow: 0 1px 0 0 rgba(22, 29, 37, 0.05);
      border: solid 1px #4a33f4;
      font-weight: 500;
      font-size: 14px;
      margin: 24px;
    }

    ^ .createControllerTable {
      background-color: #fafafa;
      border-radius: 3px;
      box-shadow: 0 24px 24px 0 rgba(0, 0, 0, 0.12), 0 0 24px 0 rgba(0, 0, 0, 0.15);
      border: solid 1px #e2e2e3;
    }
    ^ .net-nanopay-ui-ActionView-cancel {
      background-color: #fafafa;
      color: #525455;
      border: none;
      box-shadow: none;
      margin-right: 30px;
    }
  `,

  properties: [
    {
      class: 'FObjectProperty',
      of: 'foam.comics.DAOCreateController',
      name: 'data',
      factory: function() {
        return this.DAOCreateController.create({ dao: this.dao });
      }
    },
    {
      class: 'String',
      name: 'title',
      expression: function(data$dao$of) {
        return 'Create ' + data$dao$of.name;
      }
    },
    {
      class: 'String',
      name: 'detailView'
    }
  ],

  reactions: [
    [ 'data', 'finished', 'onFinished' ]
  ],

  methods: [
    function initE() {
      this.
      addClass(this.myClass()).
      start('table').addClass('createControllerTable').
        start('tr').
          start('td').style({'vertical-align': 'top', 'width': '100%'}).
            start('span').
              style({background: 'rgba(0,0,0,0)'}).
              show(this.mode$.map(function(m) { return m == foam.u2.DisplayMode.RW; })).
            end().
            tag({class: this.detailView}, {data$: this.data$.dot('data')}).
            start().
                style({'padding-bottom': '4px'}).
                add(this.data.cls_.getAxiomsByClass(foam.core.Action)).
              end().
          end().
        end().
      end();
      /*
      this.
        add(this.DAOCreateController.DATA,
            this.data.cls_.getAxiomsByClass(foam.core.Action))
            */
    }
  ],

  listeners: [
    function onFinished() {
      this.stack.back();
    }
  ]
});
