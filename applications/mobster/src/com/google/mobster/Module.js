foam.CLASS({
  package: 'com.google.mobster',
  name: 'Module',
  description: 'Module models the manifest file for a FOAM module',

  properties: [
    { name: 'name', class: 'String' },
    { name: 'description', class: 'String' },
    {
      name: 'dependancies',
      class: 'FObjectArray',
      of: 'com.google.mobster.ModuleDependancy'
    }
  ]
});

foam.CLASS({
  package: 'com.google.mobster',
  name: 'ModuleDependancy'
});

foam.CLASS({
  package: 'com.google.mobster',
  name: 'GitModuleDependancy',

  properties: [
    { name: 'uri', class: 'String' },
    { name: 'branch', class: 'String' }
  ],
});
