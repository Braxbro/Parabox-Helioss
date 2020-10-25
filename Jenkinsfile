pipeline {
  agent any
  stages {
    stage('Setup') {
      steps {
        sh 'chmod +x ./gradlew'
    }

    stage('Build') {
      steps {
        sh './gradlew build'
      }
    }

    stage('Archive') {
      steps {
        archiveArtifacts 'build/libs/*.jar'
      }
    }
      
    stage('Deploy') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'jenkinsbotnexus', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
          sh ./gradlew deploy
        }
      }
    }    

  }
}
