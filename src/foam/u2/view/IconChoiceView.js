/**
 * @license
 * Copyright 2019 Google Inc. All Rights Reserved.
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
  package: 'foam.u2.view',
  name: 'IconChoiceView',
  extends: 'foam.u2.view.ChoiceView',

  css: `
    ^ {
      display: flex;
    }

    ^item {
      display: flex;
    }

    ^ input[type="radio"] {
      display: none;
    }

    ^ label {
      cursor: pointer;
      line-height: 48px;
      margin-left: 16px;
    }


    ^disabled-icon {
      opacity: 0.2;
    }
  `,

  methods: [
    function initE() {
      // If no item is selected, and data has not been provided, select the 0th
      // entry.
      this
        .addClass(this.myClass())

      if ( ! this.data && ! this.index ) {
        this.index = 0;
      }

      if ( this.dao ) this.onDAOUpdate();
      this.choices$.sub(this.onChoicesUpdate);
      this.onChoicesUpdate();
    }
  ],

  listeners: [
    function onChoicesUpdate() {
      var self = this;
      var id;

      this.removeAllChildren();

      this.add(this.choices.map(function(c) {
        return this.E('div').
          addClass(this.myClass('item')).
          start('input').
            attrs({
              type: 'radio',
              name: this.id,
              value: c[0],
              checked: self.slot(function (data) { return data === c[0]; })
            }).
            setID(id = self.NEXT_ID()).
            on('change', function(evt) {
              self.data = c[0];
            }).
          end().
          start('label').
            attrs({for: id}).
            start('span').
              start({ 
                      class: 'foam.u2.tag.Image',
                      data: c[1]
                  }).
              end().
              enableClass(this.myClass('disabled-icon'), self.slot(function (data) { return data === c[0] })).
            end().
          end();
      }.bind(this)));
    }
  ]
});
